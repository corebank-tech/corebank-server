# VM 릴리스 교체와 이의제기 Agent 반영 런북 (#989)

> 2026-11-15 릴리스 7 교체, 2026-11-16 이의제기 Agent(P3) 반영과 리허설에서 실제로 한 순서다. 값(비밀번호, 키, 토큰)은 이 문서에 쓰지 않는다.
> 기동·정지 기본 절차는 [VM 계정계 API 런북](vm_account_api_runbook.md)을 따르고, 이 문서는 **이미지를 바꾸는 절차와 Agent를 켜는 절차**만 다룬다.
> FDS Agent(P6)는 이 문서에 없다(이미지 전달 방식과 네트워크 합의 전).

## 1. 전제

- 서버는 `corebank-server`(`--network host`, `127.0.0.1:8080`), 환경 파일 `/etc/corebank/app.env`(root `600`)를 쓴다. **`/etc/corebank-drtest/`는 격리 DB용이라 운영 서버와 무관하다.**
- 환경 변수를 바꾸면 `docker restart`가 아니라 **컨테이너를 지우고 다시 만든다**(재시작은 환경 파일을 다시 읽지 않는다).
- DB 비밀번호는 명령줄에 값으로 쓰지 않는다. `export MYSQL_PWD="$DB_PASSWORD"` 뒤 `docker exec -e MYSQL_PWD`(값 없이)로 넘긴다. `-e MYSQL_PWD="$DB_PASSWORD"`는 `ps`에 값이 보인다.
- VM 접속은 교육장 안에서 `ssh student@192.168.0.211`(3-Tier 철거 뒤에는 SSM 세션이 없다). 여러 줄을 붙여넣으면 줄이 이어 붙는 일이 있으니 **한 번에 한 명령**씩 넣는다.
- COB 시간대(23:20~00:20)에는 서버 교체와 마이그레이션을 하지 않는다.

## 2. 릴리스 이미지 교체 (11/15 릴리스 7, 릴리스 4 → 7)

1. **격리 DB 시험**: 같은 이미지를 `corebank-drtest-api`로 먼저 띄운다(`-p 127.0.0.1:18080:8080`, 운영 서버가 8080을 쓰므로 포트를 바꾼다). 헬스 200, 로그의 `Successfully applied N migrations`, 격리 DB `flyway_schema_history`의 마지막 버전과 실패 0을 확인하고 컨테이너를 지운다. 밤 COB가 격리 DB에서 돌지 않게 오래 켜 두지 않는다.
2. **승격 DB 백업**(`mysqldump`, 서비스는 멈추지 않는다). 소요 약 1분(4.8GB).
   - 폴더 `/var/backups/corebank`(root `700`)에 `minicore-YYYYMMDD-HHMM-before-release7.sql.gz`.
   - 옵션 `--single-transaction --quick --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4`, `app_user`(SELECT)로 충분하다.
   - 검증: 종료 코드 0, `gzip -t`, 마지막 줄 `-- Dump completed`, `CREATE TABLE` 수가 DB 테이블 수와 같음(54), `sha256sum` 기록.
   - 한계: 같은 VM 디스크라 VM 손실은 막지 못한다. 폴더가 `700`이라 `ls`와 `*` 확장은 `sudo` 안에서(`sudo bash -c 'ls …/*.gz'`) 해야 한다.
3. **서버 컨테이너 교체**(약 1분 502). **[VM 계정계 API 런북](vm_account_api_runbook.md) §5의 릴리스 4 기동 명령을 그대로 쓰지 않는다.** 그 명령은 릴리스 4(`e6dfafe4`) 이미지를 고정하고 있어 Agent API가 없는 버전으로 돌아가며, 문서 볼륨과 `-e` 값이 빠져 있어 재생성 뒤 기존 첨부파일을 읽지 못한다. 현재 기준 절차는 아래다(`<새 이미지 SHA>`는 실제 값으로 바꾼다. 11/17 릴리스 8은 `69f2df964420ac3158748a7dcd254585231deafa`).
   - **교체 전 확인 ①(이름 충돌, 읽기 전용)**: `corebank-server-prev`가 이미 있으면 아래 교체 명령의 `rename`이 실패해 **서버가 내려간 채로 남는다.** 나오면 정지하지 말고, 옛 롤백 대상을 보존하려면 `sudo docker rename corebank-server-prev corebank-server-prev-$(date +%m%d%H%M)`로 이름을 바꾼 뒤 진행한다.
   ```bash
   sudo docker ps -a --format '{{.Names}}' | grep -x corebank-server-prev && echo "이미 있다. 이름을 바꾸거나 지운 뒤 진행" || echo "이름 충돌 없음"
   ```
   - **교체 전 확인 ②(Flyway 마지막 버전 기록, 읽기 전용)**: 복귀 조건 판단에 쓴다. 교체 전에 이 값을 적어 둔다.
   ```bash
   sudo bash -c 'MYSQL_PWD=$(cat /etc/info-etl/replica-root.pw) docker exec -i -e MYSQL_PWD info-replica-mysql mysql -uroot -N minicore -e "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1"'
   ```
   - **교체 명령**: 한 줄 명령으로 실행한다(`&&`로 이어 앞 단계 실패 시 멈춤).
   ```bash
   sudo docker stop corebank-server && sudo docker rename corebank-server corebank-server-prev && sudo docker run -d --name corebank-server --network host --cpus 1 --memory 2g -v /var/lib/corebank-audit:/audit -v /var/lib/corebank-objection-docs:/objection-docs --env-file /etc/corebank/app.env --env-file /etc/corebank/app-extra.env corebank/corebank-server:<새 이미지 SHA>
   ```
   - 필수 변경 항목: 이미지 태그, 볼륨 2개(`/var/lib/corebank-audit:/audit`, `/var/lib/corebank-objection-docs:/objection-docs`), 환경 파일 2개. `app-extra.env`(root `600`)는 `app.env`에 없고 `-e`로 따로 준 값(`APP_AUDIT_ARCHIVE_LOCAL_DIR`, `APP_INTERBANK_HOST`, `APP_METRICS_ALLOWED_IP`, `JAVA_TOOL_OPTIONS`, `PAYMENT_STOP_DOCUMENT_ROOT`, `SERVER_ADDRESS`)을 옮겨 둔 파일이다. 처음 만들 때는 실행 중인 컨테이너에서 그 이름의 값만 뽑아 파일로 저장한다(값은 화면에 나오지 않는다): `sudo bash -c 'umask 077; docker inspect corebank-server --format "{{range .Config.Env}}{{println .}}{{end}}" | grep -E "^(APP_AUDIT_ARCHIVE_LOCAL_DIR|APP_INTERBANK_HOST|APP_METRICS_ALLOWED_IP|JAVA_TOOL_OPTIONS|PAYMENT_STOP_DOCUMENT_ROOT|SERVER_ADDRESS)=" > /etc/corebank/app-extra.env'`
   - 이전 컨테이너는 지우지 않고 이름만 바꿔 둔다(`corebank-server-prev`). 시연 뒤 지운다.
   - **복귀는 새 이미지가 마이그레이션을 적용했는지로 나눈다.** 교체 후 같은 조회(확인 ②)로 마지막 버전을 다시 본다.
     - **버전이 교체 전과 같다(적용 안 됨)**: 이름만 되돌린다. `rm -f`는 `;`로 분리해, 새 컨테이너가 만들어지지 않아 없는 경우에도 뒤 단계가 실행되게 한다.
       ```bash
       sudo docker rm -f corebank-server; sudo docker rename corebank-server-prev corebank-server && sudo docker start corebank-server
       ```
     - **버전이 올라갔다(적용됨)**: 이전 이미지를 바로 시작하지 않는다. 새 서버를 멈춘 채(`sudo docker stop corebank-server`) 쓰기를 막고, 백업 복원 여부를 §2-5 기준으로 따로 판단한다.
   - 이 복귀 분기와 이름 충돌 확인의 `rename` 실패 방지는 **VM에서 실제로 시험하지 않았다**(서버가 약 1분 502가 되므로 시연 전에는 돌리지 않았다). 이름 충돌 확인 ①만 11/17에 VM에서 확인했다(충돌 없음).
   - 서버 컨테이너는 `restart=no`라 VM이 재부팅되면 자동으로 올라오지 않는다.
4. **확인**: 컨테이너 이미지 태그, 헬스 200, 로그 `Successfully applied`·`Started CorebankApplication`·`ERROR` 없음, 승격 DB `flyway_schema_history`(마지막 버전, 실패 0), 외부 `api`·`www` 200과 `/internal/`·`prometheus` 404, 화면 로그인·이체 1건과 원장·GL·잔액·알림 정합.
5. **되돌리기**: 새 이미지가 마이그레이션을 적용하지 않았으면 §2-3의 이름 되돌리기로 복귀한다. **마이그레이션이 적용된 뒤에는 이전 이미지로 되돌리는 것이 안전하지 않을 수 있다**(시험하지 않았다). 그래서 §2-3에서도 그 경우에는 이전 이미지를 시작하지 않는다. 그때의 정식 롤백은 백업 복원(root 필요, `/etc/info-etl/replica-root.pw`)이다.
   - **한계**: 백업은 서비스를 켠 채 받으므로 백업 이후 생긴 거래는 복원하면 사라진다(그 거래를 다시 넣는 절차는 시험하지 않았다). 롤백 결정이 날 때까지 쓰기를 막으려면 서버를 멈춘 채 백업해야 하며, 11/15에는 그렇게 하지 않았다.

## 3. 이의제기 Agent 반영 (11/16)

P3가 빌드 재료와 시연 데이터를 전달한다(워커 빌드 재료는 평문 프롬프트가 들어 있어 1:1 전달, 사용 뒤 삭제).

1. **전송**: 맥에서 `scp`로 VM `~/agent-deploy/`(`umask 077`)에 올리고 `sha256sum -c`로 확인한다.
2. **사전 점검**(읽기 전용): 약관 `TERMS_SERVICE`·`TERMS_PRIVACY` v1.0이 2개, `agent*` 계정 0개, 지급정지 권한 코드 3개, GL 계정 `10100`·`20100`, 계좌번호 시퀀스. 환경 파일에 `PII`·`PAYMENT_STOP`·`FDS` 변수가 없는지(`app.pii.read-encrypted`가 true면 시연 고객 로그인이 깨진다).
3. **키 생성과 서버 환경 파일**: 환경 파일을 백업하고(`app.env.bak-before-agent`), VM에서 워커 키(`openssl rand -hex 32`)를 만들어 `PAYMENT_STOP_AGENT_ENABLED=true`, `PAYMENT_STOP_WORKER_KEY`를 추가한다. 문서 암호화 키(`openssl rand -base64 32`)는 환경 파일에 `PAYMENT_STOP_DOCUMENT_KEY`가 **없거나 값이 비어 있을 때만** 만들어 넣는다. 값이 있는 줄이 이미 있으면 그대로 둔다. 확인은 서버(`LocalDocumentStorage.parseKey`)와 같은 기준(공백 제거 → base64 → 32바이트)이며 값은 출력하지 않는다.
   ```bash
   sudo bash -c 'v=$(grep -E "^PAYMENT_STOP_DOCUMENT_KEY=" /etc/corebank/app.env | tail -1 | cut -d= -f2- | tr -d "[:space:]"); if [ -z "$v" ]; then echo "없음 또는 빈 값"; elif [ ${#v} -eq 44 ] && printf %s "$v" | base64 -d >/dev/null 2>&1 && [ "$(printf %s "$v" | base64 -d | wc -c)" -eq 32 ]; then echo "유효(base64 32바이트)"; else echo "형식 오류"; fi'
   ```
   - `유효`: 만들지 않는다. `없음 또는 빈 값`: 빈 줄(`PAYMENT_STOP_DOCUMENT_KEY=`)을 지우고 새로 만든다.
   - **`형식 오류`: 새 키를 만들지 않는다.** `/var/lib/corebank-objection-docs`에 암호화된 첨부가 있으면 이전 키를 `app.env.bak-before-agent` 등 백업에서 복구한다. 첨부가 없을 때만 새로 만든다.
   - 이 검증 명령은 맥에서 무작위 임시 값 8가지(유효·빈 값·공백뿐·줄 없음·16바이트·base64 아님·패딩 없음·뒤 공백)로 시험했고, 11/17 VM(GNU `base64`)에서 운영 `app.env`의 실제 키로 돌려 `유효`가 나왔다. 형식 오류 쪽 분기는 VM에서 시험하지 않았다.
   - 이어서 같은 워커 키로 `worker.env`(`CORE_SERVER_URL=http://127.0.0.1:8080/api/v1`, `AGENT_LLM=claude` 등)를 만든다. Claude API 키는 `read -rs`로 VM에서 직접 입력한다(`bash`에서만 동작, SSM 기본 셸 `sh`는 불가).
   - **`PAYMENT_STOP_DOCUMENT_KEY`는 한 번 정하면 바꾸지 않는다**(바꾸면 저장한 신분증 사본을 못 읽는다).
4. **서버 컨테이너 재생성**: 첨부 볼륨 `-v /var/lib/corebank-objection-docs:/objection-docs -e PAYMENT_STOP_DOCUMENT_ROOT=/objection-docs`를 포함해 다시 만든다.
5. **워커 이미지 빌드**: 압축을 풀어 `docker build -t objection-worker:release7-878cc1c <폴더>`. 이미지는 공개 레지스트리에 올리지 않는다. **이미지 ID는 빌드 시각이 들어가 P3의 값과 다를 수 있다**(정상). 내용 확인은 이미지 안 `.py` 파일별 해시를 원본과 비교한다(`LC_ALL=C sort`로 정렬을 맞춘다). 빌드 뒤 평문 소스와 압축 파일을 지운다.
6. **워커 환경 파일과 실행**: `worker.env`를 `/etc/corebank/worker.env`(root `600`)로 옮기고 홈의 사본을 지운다. 재실행이면 먼저 `sudo docker rm -f objection-worker`로 기존 워커를 지운다(워커 키가 새로 만들어져 이전 워커는 인증하지 못한다. 처음 실행이면 건너뛴다). `sudo docker run -d --name objection-worker --restart unless-stopped --network host --read-only --tmpfs /tmp:size=512m,mode=1777 --memory 2g --cap-drop ALL --security-opt no-new-privileges --env-file /etc/corebank/worker.env objection-worker:release7-878cc1c`. 로그에 `워커 시작 … llm=claude`가 나온다.
7. **시연 데이터 적재**(`agent_demo_seed.sql`): **root로 실행한다**(`CREATE TEMPORARY TABLE` 때문에 `app_user`·`flyway_user`는 권한이 없다). `/etc/info-etl/replica-root.pw`를 `MYSQL_PWD=$(cat …)` 접두 대입으로 넘기면 `ps`에 값이 안 보인다. 한 트랜잭션이라 사전 검사나 커밋 직전 검증(잔액=원장, 차변=대변, 전표 17건, 약관 동의 22건)이 어긋나면 전체가 롤백되고, 다시 실행해도 사전 검사에서 멈추는 것이 정상이다.
8. **등록 스크립트**(`register_agent_cases.py`): 사건 4건 등록과 C·B 대리 접수. 운영 세션 쿠키가 `Secure`라 `http://127.0.0.1`로는 로그인 뒤 호출이 실패할 수 있어 공개 주소 `https://api.corebank.cloud/api/v1`로 실행한다. **Cloudflare가 파이썬 기본 `User-Agent`를 403(오류 1010)으로 막으므로** 요청 표식을 바꿔 실행한다(`urllib.request.build_opener`가 만드는 opener의 `addheaders`를 바꾸는 한 줄 래퍼, 스크립트 파일은 수정하지 않는다). 1010 오류는 서버에 도달하지 않아 로그인 실패 횟수가 늘지 않는다.

## 4. 확인 (리허설)

1. 관리자 `agentoperator`로 `/admin/login`에서 로그인 후 `/admin/objections`: 사건 C·B가 분석 `완료`, C 상세에 "서버 규칙 결과와 AI 의견이 다름"과 주장 모순. (워커 로그의 `판정안 제출` 두 줄: C `UNDETERMINED`, B `FRAUD_PATTERN`.)
2. 고객 `agentcase`: `/support/payment-stops`에서 이의제기 신청(신분증 사본 업로드) → 접수 직후 AI 분석 약 25초 → 관리자 상세 타임라인.
   - 서버 볼륨에 `/var/lib/corebank-objection-docs/<이의제기번호>/<서류번호>`가 암호화되어(원본 + 28바이트) 저장된다.
3. 보완 흐름: 관리자가 `보완 요청 작성`(최대 2회) → 고객 `보완 서류 제출`(**서류만** 받고 소명 원문은 바꿀 수 없다, 1사건 1이의제기) → OCR(`tesseract kor+eng`) 판독과 재분석.
4. `소명 완비 확인` → `수용`(판정 사유, 조기 해제 근거) → **다른 관리자**(`adminapprover`)가 승인 → 지급정지 해제, 고객 화면에도 반영.
5. 사건 A를 리허설에 쓰면 소진된다. 본 발표는 A2(`agentcase2`)로 하고, 처음부터 구체적인 소명으로 접수한다. C·B는 분석 결과만 보여 주는 용도로 판정하지 않는다.

## 5. 끄기와 정리

- 분석만 멈추기: `sudo docker stop objection-worker`(접수 건은 대기열에 남는다). 켜 둬도 사건을 접수하거나 재분석을 요청하지 않으면 Claude 호출은 없다(분석 1건 약 $0.06).
- 분석 작업 가져가기 끄기: `PAYMENT_STOP_AGENT_ENABLED=false`로 서버 재생성(키는 환경 파일에 그대로 둔다). 워커는 새 작업을 받지 못한다. 이 설정은 접수와 재분석 요청을 막지 않으며(접수 중지 방법은 이 절차에 없다) 그 작업은 대기열에 남는다.
- 발표 뒤: Claude API 키를 Console에서 폐기한다. VM의 `~/agent-deploy`에는 시드 SQL과 계좌 목록이 남으므로 필요 없으면 지운다.

## 6. 문제와 대응

| 증상 | 원인 | 대응 |
|---|---|---|
| 등록 스크립트 `HTTP 403 error code: 1010` | Cloudflare가 파이썬 기본 User-Agent를 봇으로 차단 | 요청 표식을 바꿔 실행 |
| 시연 SQL `CREATE TEMPORARY TABLE` 권한 오류 | `app_user`·`flyway_user`에 임시 테이블 권한 없음 | root로 실행 |
| 워커 이미지 안에 `._*.py` 14개 | 맥에서 만든 압축 파일의 메타데이터(AppleDouble) | 동작 영향 없음, 압축할 때 `COPYFILE_DISABLE=1` |
| `sudo ls …/*.gz`가 파일 없음 | 폴더가 `700`이라 `*`가 내 계정의 셸에서 풀림 | `sudo bash -c '…'` 안에서 확장 |
| 확인용 `ps`에 DB 비밀번호가 보임 | `-e MYSQL_PWD="$DB_PASSWORD"` | `export` 후 `-e MYSQL_PWD`(값 없이) |
| 보완 제출 화면에 버튼이 없음 | 보완은 `보완 요청` 상태에서만 열림 | 관리자가 먼저 보완 요청 |
| 붙여넣은 여러 줄이 이어 붙음 | 터미널 붙여넣기 | 한 줄 명령으로 실행 |

## 7. 관련 문서

- [VM 계정계 API 런북](vm_account_api_runbook.md), [PH-54 DR 런북](ph54_onprem_dr_runbook.md)
- [이의제기 Agent 서버 계약](objection_agent_contract.md), [이의제기 관리 계약](objection_admin_contract.md)
