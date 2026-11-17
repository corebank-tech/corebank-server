# VM FDS Agent(P6) 반영 런북 (#857)

> 2026-11-17에 시연 VM에 FDS Agent를 올리고 서버와 연결해서 실제로 한 순서다. 값(비밀번호, 키, 토큰)은 이 문서에 쓰지 않는다.
> 서버 이미지 교체와 이의제기 Agent는 [VM 릴리스 교체와 Agent 런북](vm_release_agent_runbook.md)(PR #1017), 서버 기동 기본 절차는 [VM 계정계 API 런북](vm_account_api_runbook.md)(PR #1007)을 따른다. 이 문서는 **FDS Agent를 올리는 절차**만 다룬다.
> **구분**: 아래 명령은 11/17에 VM에서 실제로 실행한 것이고, 확인하지 못한 것은 "미확인"으로 적었다.

## 1. 구성

| 항목 | 값 |
|---|---|
| Agent 이미지 | `corebank-fds-agent:<Agent 전체 Git SHA>`(11/17 `93198810e51e79a1961f2386f6d5f90812d56ccb`). P6 비공개 저장소의 소스를 VM에서 직접 빌드한다(공개 레지스트리에 올리지 않는다) |
| 실행 | `--network host`, Uvicorn `127.0.0.1:8000`. 서버(`127.0.0.1:8080`)와 같은 VM에서 서로 loopback으로만 부른다 |
| Agent 환경 파일 | `/etc/corebank/fds-agent.env`(root `600`), 서버 환경 파일 `/etc/corebank/app.env`(root `600`) |
| 서버 이미지 | FDS 조사 스케줄러 수정(#1016)이 들어간 릴리스 8 이상. **릴리스 7은 조사 작업이 `PENDING`에 멈춘다**(§9) |
| 외부 LLM | 쓰지 않는다. 행동 점수는 이미지 안의 XGBoost 모델, 조사는 정해진 순서의 워크플로다. Claude API 키가 필요 없다 |

## 2. 소스 확인과 빌드

1. 맥에서 `scp`로 VM `~/fds-deploy/`(`umask 077`)에 올리고 압축 파일의 SHA-256을 P6가 준 값과 대조한다.
```bash
echo "<P6가 준 sha256>  source.tar.gz" | sha256sum -c
```
2. 새 폴더에 푼다. **압축 파일은 줄바꿈이 CRLF일 수 있다.** 에이전트는 시작할 때 `feature_schema.json`과 `model.json`의 해시를 `inference_config.json`의 기대값과 비교하는데, 기대값은 LF 기준이다. CRLF 그대로 빌드하면 `ModelVersionMismatch`로 모델이 안 올라와 `/health`가 503이 된다(11/17 실제).
```bash
mkdir -p src && tar -xzf source.tar.gz -C src
```
3. 줄바꿈을 LF로 바꾼 뒤 두 파일의 해시가 P6 목록과 같은지 확인한다. 아래 값은 11/17 소스(`93198810`)의 값이고, 소스가 바뀌면 P6 목록의 값을 쓴다.
```bash
cd src && sed -i 's/\r$//' fds_agent/*.py fds_agent/*.json fds_agent/data/scale_50k_v1/expanded/training_config.json && sha256sum fds_agent/feature_schema.json fds_agent/data/scale_50k_v1/expanded/model.json
```
- 기대: `feature_schema.json` = `6cf8a759…`, `model.json` = `c0e7d6b2…`. P6의 `release-files.sha256`·`python-files.sha256`은 LF 기준이라 CRLF 원본에 그대로 `sha256sum -c`하면 대부분 불일치로 나온다(내용은 같다).
4. 이미지를 빌드한다(`.dockerignore`가 필요한 파일만 보내서 빌드는 가볍다. 150MB 학습 데이터는 이미지에 들어가지 않는다).
```bash
sudo docker build --build-arg GIT_SHA=<Agent 전체 Git SHA> -t corebank-fds-agent:<Agent 전체 Git SHA> .
```
5. 이미지에 기록된 커밋이 SHA와 같은지 확인한다.
```bash
sudo docker image inspect corebank-fds-agent:<Agent 전체 Git SHA> --format '{{index .Config.Labels "org.opencontainers.image.revision"}}'
```

## 3. 환경 파일과 토큰

토큰 3개(`FDS_SCORE_SERVICE_TOKEN`, `FDS_INVESTIGATION_SERVICE_TOKEN`, `FDS_AGENT_SERVICE_TOKEN`)는 서로 달라야 하고, 서버와 Agent 파일에 같은 값을 넣는다. **채팅 · 문서 · 명령줄 인자에 값을 쓰지 않는다.** 아래는 값이 화면에 나오지 않고 `printf`가 셸 내장이라 `ps`에도 남지 않는다. 서버 환경 파일은 먼저 백업한다(`sudo cp -p /etc/corebank/app.env /etc/corebank/app.env.bak-before-fds`).

```bash
sudo bash -c 'umask 077; s=$(openssl rand -hex 32); i=$(openssl rand -hex 32); a=$(openssl rand -hex 32); printf "FDS_SCORE_SERVICE_TOKEN=%s\nFDS_INVESTIGATION_SERVICE_TOKEN=%s\nFDS_AGENT_SERVICE_TOKEN=%s\nFDS_BE_INTERNAL_BASE_URL=http://127.0.0.1:8080\n" "$s" "$i" "$a" > /etc/corebank/fds-agent.env; [ -n "$(tail -c1 /etc/corebank/app.env)" ] && echo >> /etc/corebank/app.env; printf "APP_FDS_POLICY_ENABLED=true\nAPP_FDS_SCORE_ENABLED=true\nAPP_FDS_RISK_SEED_ENABLED=false\nAPP_FDS_SCORE_BASE_URL=http://127.0.0.1:8000\nAPP_FDS_INVESTIGATION_ENABLED=true\nAPP_FDS_INVESTIGATION_BASE_URL=http://127.0.0.1:8000\nFDS_SCORE_SERVICE_TOKEN=%s\nFDS_INVESTIGATION_SERVICE_TOKEN=%s\nFDS_AGENT_SERVICE_TOKEN=%s\n" "$s" "$i" "$a" >> /etc/corebank/app.env; echo done'
```

확인(개수만, 값은 나오지 않는다):
```bash
sudo bash -c 'echo "서버 FDS 변수: $(grep -c -E "^(APP_FDS|FDS_)" /etc/corebank/app.env) (기대 9)"; echo "에이전트 변수: $(grep -c . /etc/corebank/fds-agent.env) (기대 4)"; echo "토큰 서로 다른 개수: $(grep -E "^FDS_.*_TOKEN=" /etc/corebank/fds-agent.env | cut -d= -f2 | sort -u | wc -l) (기대 3)"; echo "토큰 길이: $(grep -E "^FDS_SCORE_SERVICE_TOKEN=" /etc/corebank/fds-agent.env | cut -d= -f2 | tr -d "\n" | wc -c) (기대 64)"; echo "서버·에이전트 토큰 일치: $(diff <(grep -E "^FDS_.*_TOKEN=" /etc/corebank/app.env | sort) <(grep -E "^FDS_.*_TOKEN=" /etc/corebank/fds-agent.env | sort) >/dev/null && echo 일치 || echo 불일치)"'
```
- 서버 `APP_FDS_RISK_SEED_ENABLED`는 **`false`로 둔다.** 시드 SQL로 만든 위험 데이터셋(`demo-risk-v1`)을 서버의 자동 로더가 CSV 해시로 다시 적재하려 하면 충돌해 서버 기동이 막힐 수 있다(`loadAndActivate`).
- 릴리스 8부터 서버는 기동할 때 토큰이 없거나 서로 같으면 기동을 실패시킨다(#1016). 위 확인이 통과하면 괜찮다.

## 4. Agent 기동과 확인

```bash
sudo docker run -d --name corebank-fds-agent --restart unless-stopped --network host --read-only --tmpfs /tmp:size=64m,mode=1777 --cap-drop ALL --security-opt no-new-privileges --cpus 1 --memory 1g --env-file /etc/corebank/fds-agent.env corebank-fds-agent:<Agent 전체 Git SHA> python -m uvicorn fds_agent.api:app --host 127.0.0.1 --port 8000 --log-level info
```
- P6 확정본과 다른 점: tmpfs에 `noexec,nosuid`를 붙이지 않았고, 환경 파일은 `secrets/` 하위가 아니다(미적용, 동작 영향 미확인).
- 확인(한 줄씩): `status=running health=healthy restart=0`, `/health` 200 `{"status":"ok"}`, **`127.0.0.1:8000`만 listen**.
```bash
sudo docker inspect corebank-fds-agent --format 'status={{.State.Status}} health={{.State.Health.Status}} restart={{.RestartCount}}'
```
```bash
curl -s -i http://127.0.0.1:8000/health | head -3
```
```bash
sudo ss -lntp | grep ':8000'
```
- `0.0.0.0:8000`이나 VM 외부 주소가 보이면 즉시 `sudo docker stop corebank-fds-agent`.
- `/health` 503이면 모델 해시 불일치(줄바꿈, §2)나 토큰 설정 오류다. 로그의 마지막 줄을 본다.

## 5. 서버 교체

[릴리스 런북](vm_release_agent_runbook.md) §2-3의 현재 기준 명령을 그대로 쓴다(릴리스 8 이상 이미지, 볼륨 2개, `app.env`와 `app-extra.env`). 새 환경 변수는 `app.env`에 이미 넣었으므로 서버 컨테이너를 **다시 만들어야** 읽는다(재시작은 환경 파일을 다시 읽지 않는다). COB 시간대(23:20 ~ 00:20)에는 하지 않는다.

확인:
```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/v1/actuator/health
```
```bash
sudo docker logs corebank-server 2>&1 | grep -E "Started CorebankApplication|ERROR|APPLICATION FAILED|must be configured|must use distinct" | cut -c1-200 | head
```
- **조사 스케줄러가 생겼는지**(기동 1분 이상 뒤, `1`이어야 한다):
```bash
curl -s http://127.0.0.1:8080/api/v1/actuator/prometheus | grep -E '^tasks_scheduled_execution_seconds_count' | grep -c FdsInvestigationScheduler
```
- 이미 `PENDING`으로 멈춰 있던 조사 작업은 릴리스 8 기동 직후 첫 실행에서 `COMPLETED`가 됐다.

## 6. 시드(위험계좌와 부스 계정)

P6가 준 시드 SQL로 위험 데이터셋(`demo-risk-v1`)과 부스 계정 20개 · 위험계좌 2건 · 예비 계정 6명을 만든다. **P6가 처음 준 v2는 그대로 실행하지 않았다.** 점검 순서를 지킨다.

1. **정적 점검**: ① 대상 테이블 · 열이 `dev` 마이그레이션에 있는지, ② **계산(`GENERATED ALWAYS`) 열에 값을 넣지 않는지**(`fds_risk_account.admin_account_key`에 `NULL`을 넣으면 MySQL 3105), ③ 정책 코드(`A`/`B`)와 보고서 모양이 실제 서버·에이전트 출력과 같은지, ④ 삭제한 구간의 변수를 남은 SQL이 참조하지 않는지, ⑤ `CREATE PROCEDURE` 같은 자동 커밋 구문이 없는지.
2. **격리 DB에서 실행**(`corebank-drtest-mysql`, 접속은 컨테이너 안 `/run/secrets/root_pw`). 오류 없이 끝나야 한다.
```bash
sudo docker exec -i corebank-drtest-mysql sh -c 'MYSQL_PWD=$(cat /run/secrets/root_pw) mysql --default-character-set=utf8mb4 -uroot minicore' < ~/fds-deploy/<시드 SQL> && echo "시드 실행 완료(오류 없음)"
```
3. **같은 명령을 한 번 더** 실행해 `ERROR 3819 … ck_fds_seed_guard`로 **사전 검사에서 멈추는지** 확인한다(중복 적재 방지). 멈추지 않으면 중단한다.
4. **운영 DB 백업**(서버를 켠 채로 약 1분, 11/17 약 280MB 압축본)과 검증(`gzip -t`, 마지막 줄 `-- Dump completed`, `CREATE TABLE` 수 = 실제 테이블 수).
```bash
sudo bash -c 'set -o pipefail; MYSQL_PWD=$(cat /etc/info-etl/replica-root.pw) docker exec -i -e MYSQL_PWD info-replica-mysql mysqldump -uroot --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 minicore | gzip > /var/backups/corebank/minicore-$(date +%Y%m%d-%H%M)-before-fds-seed.sql.gz && echo "백업 완료"'
```
5. **운영 적용**(root 필요: 시드가 `CREATE TEMPORARY TABLE`을 쓴다).
```bash
sudo bash -c 'MYSQL_PWD=$(cat /etc/info-etl/replica-root.pw) docker exec -i -e MYSQL_PWD info-replica-mysql mysql --default-character-set=utf8mb4 -uroot minicore' < ~/fds-deploy/<시드 SQL> && echo "운영 시연 DB 시드 적용 완료"
```
6. 확인: 시드 계정 28, 위험계좌 2건, 활성 버전 `demo-risk-v1`, `account_number_sequence`가 마지막 번호로 이동, 기존 시연 계좌 변경 없음. **계좌 번호는 운영의 현재 채번에서 이어지므로 격리 DB와 다르다.** 적용 뒤 조회해서 대본에 쓴다.

- 11/17 운영 결과: 부스 계정 `fdstest01~20` = `088106050026` ~ `045`, `riskaccount01` = `088106050046`, `riskaccount02` = `088106050047`, 예비 6명 `088106050048` ~ `053`.
- 예비 계정(`fdspresnd01~03`, `fdsprehold01~03`)은 계정 · 계좌 · 개시 원장만 있고 **보류 사건 · AI 보고서는 없다.**
- 시드 이후 위험계좌 칸이 `UNAVAILABLE`(사유 `ACTIVE_DATASET_MISSING`)에서 `NO_MATCH`/`MATCH`로 바뀐다.

## 7. 관리자 권한 `FDS_FRAUD_CONFIRM`

"사기 확정 및 거절"은 이 권한이 있는 관리자만 쓴다. 부여는 관리자 권한 API(`POST /admin/customers/{customerId}/permissions`, 로그인 · `X-XSRF-TOKEN` · `Idempotency-Key` 필요)로 하고, **자기 자신에게는 줄 수 없고 `CUSTOMER_WRITE`를 가진 다른 관리자만 줄 수 있다.** 이 VM에서는 `CUSTOMER_WRITE`를 가진 관리자가 하나뿐이라 2단계로 했다.

1. `CUSTOMER_WRITE`가 있는 관리자 A가 다른 관리자 B에게 `CUSTOMER_WRITE`를 부여한다.
2. B가 새로 로그인해(권한은 다시 로그인한 뒤부터 적용된다) A에게 `FDS_FRAUD_CONFIRM`을 부여한다.
3. A가 B의 임시 `CUSTOMER_WRITE`를 회수한다(`DELETE …/permissions/CUSTOMER_WRITE`, **이 호출에도 `Idempotency-Key`가 필요하다**).
4. A는 로그아웃 후 다시 로그인한다. 권한 현황은 `admin_permission`을 읽기 전용으로 조회해 확인한다.

- 로그인 응답의 세션 쿠키가 `Secure`라 VM의 `http://127.0.0.1`이 아니라 공개 주소(`https://api.corebank.cloud/api/v1`)로 호출한다. 비밀번호는 `read -s`로만 입력하고, **로그인이 실패하면 같은 계정으로 연속 시도하지 않는다**(잠긴다).
- 호출 도구 `admin_perm.sh`(맥에서 `scp`로 올려 사용):

```bash
#!/usr/bin/env bash
# 사용: admin_perm.sh grant|revoke <로그인할 관리자 아이디> <대상 customerId> <PERMISSION>
set -euo pipefail
BASE="${BASE:-https://api.corebank.cloud/api/v1}"
MODE="${1:-}"; ACTOR="${2:-}"; TARGET="${3:-}"; PERM="${4:-}"
[[ "$MODE" == grant || "$MODE" == revoke ]] || { echo "사용: $0 grant|revoke <관리자아이디> <대상customerId> <PERMISSION>" >&2; exit 2; }
[[ "$TARGET" =~ ^[0-9]+$ ]] || { echo "대상 customerId는 숫자여야 합니다" >&2; exit 2; }
[[ "$PERM" =~ ^[A-Z_]+$ ]] || { echo "PERMISSION 형식 오류" >&2; exit 2; }
read -rsp "${ACTOR} 비밀번호: " PW; echo
JAR="$(mktemp)"; trap 'rm -f "$JAR"' EXIT
BODY="$(U="$ACTOR" P="$PW" python3 -c 'import json,os;print(json.dumps({"userId":os.environ["U"],"password":os.environ["P"]}))')"
unset PW
CODE="$(printf '%s' "$BODY" | curl -sS -o /dev/null -w '%{http_code}' -c "$JAR" -H 'Content-Type: application/json' --data-binary @- "$BASE/admin/auth/login")"
unset BODY
echo "로그인 HTTP $CODE"
[[ "$CODE" == 200 ]] || { echo "로그인 실패. 같은 계정으로 연속 시도하면 잠길 수 있으니 여기서 멈춥니다." >&2; exit 1; }
XSRF="$(awk '$6=="XSRF-TOKEN"{print $7}' "$JAR" | tail -1)"
[[ -n "$XSRF" ]] || { echo "XSRF-TOKEN 쿠키를 못 받았습니다" >&2; exit 1; }
IDEM="$(python3 -c 'import uuid;print(uuid.uuid4())')"
if [[ "$MODE" == grant ]]; then
  curl -sS -w '\nHTTP %{http_code}\n' -b "$JAR" -X POST -H "X-XSRF-TOKEN: $XSRF" -H "Idempotency-Key: $IDEM" \
    -H 'Content-Type: application/json' -d "{\"permission\":\"$PERM\"}" "$BASE/admin/customers/$TARGET/permissions"
else
  curl -sS -w '\nHTTP %{http_code}\n' -b "$JAR" -X DELETE -H "X-XSRF-TOKEN: $XSRF" -H "Idempotency-Key: $IDEM" \
    "$BASE/admin/customers/$TARGET/permissions/$PERM"
fi
```

## 8. 시험 결과와 시간 (11/17, VM)

시험 송금자는 시연 계정이 아닌 PH-60b 시험 계정(`ph60b07931`)이다. 정책 판정은 같은 고객이 같은 수취계좌로 **10분 창(요청 시각 − 10분부터)** 안에 3건 이상 보낼 때 걸린다. A는 합계 150만 원 이상 + 창의 첫 성공 이체 이전에 이 수취인 이력이 없음(신규 수취인) + 창 안 망설임(확인 화면 뒤로가기 2회 · 금액 재수정 2회 · 활성 체류 60초 중 2개 이상)이고, **A가 먼저 판정된다.** A가 아니고 합계 300만 원 이상이면 B다.

| 사건 | 정책 | 건수 · 금액 | 위험계좌 | 처리와 결과 |
|---|---|---|---|---|
| 1 | B | 3 · 3,000,000원 | `UNAVAILABLE`(시드 전) → `NO_MATCH` | 고객 부인 → 일반 거절(구 FE라 행동 기록 없음, 점수 `UNAVAILABLE`) |
| 2 | B | 3 · 3,000,000원 | **`MATCH`** | 위험 계좌 일치 거절(`RISK_ACCOUNT_MATCH`), 승인 비활성. 행동 점수 `AVAILABLE`(0.0089) |
| 3 | **A** | 3 · 1,600,000원 | `NO_MATCH` | 고객 확인 → **승인**, `EXECUTED`, 원장 `WITHDRAWAL`/`DEPOSIT` 2행(각 500,000원) |
| 4 | **A** | 4 · 3,600,000원 | `NO_MATCH` | 고객 부인 → **사기 확정 및 거절**(`FRAUD_CONFIRMED`), 수취 계좌가 위험 데이터셋에 `ADMIN_CONFIRMED`로 영구 등록 |

- **시간**(사건 2, 1건): 보류 → 행동 점수 0.7초, 보류 → 조사 보고서 **22.8초**. 서버 스케줄러가 1분 주기라 보고서까지 **최대 약 60초**다.
- 행동 이벤트는 이체 제출(`SUBMIT_REQUESTED`)까지 포함된 흐름이 확정돼 기록됐다(`submitted=1`, 확정 시각 채워짐, 뒤로가기 2회 · 재수정 2회 · 활성 체류 22초). 제출 전에 중단한 흐름이 기록되는지는 확인하지 않았다.
- 같은 수취인에게 앞선 이체에 망설임이 한 번 있으면 이후 건도 정책 A다. 정책 A의 "신규 수취인"은 송금자-수취인 쌍당 한 번이라 수취 계정마다 A 시연은 1회다. 정책 B는 10분이 지나면 같은 수취인에게 다시 시연할 수 있다.
- 사기 확정은 위험 데이터셋을 바꾸므로 시험에는 시연 계정이 아닌 예비 계정을 쓴다.
- 에이전트는 승인 · 거절을 판단하지 않는다(`recommendedAction`은 `MANUAL_CUSTOMER_CONFIRMATION` 하나). 조사 보고서는 이력 조회 요약과 발견 사항(`REGULAR_SPLIT_AMOUNTS` 등), 고객 질문 제안이다.

## 9. 알게 된 함정

| 증상 | 원인 | 대응 |
|---|---|---|
| `/health` 503 | 소스가 CRLF라 `feature_schema.json` 해시가 기대값(LF 기준)과 달라 모델이 로드되지 않음 | §2의 LF 변환 뒤 다시 빌드 |
| 조사 작업이 `PENDING`, `attempt_count` 0, 에이전트 로그에 `/health`뿐 | 릴리스 7 서버에서 `FdsInvestigationScheduler`의 `@ConditionalOnBean`이 빈 등록 순서 때문에 거짓이 되어 스케줄러 빈이 생성되지 않음. 설정은 정상이고 지표에 이 스케줄러만 없다 | #1016이 포함된 릴리스 8로 교체. 확인은 §5의 지표 한 줄 |
| 시드가 3105로 실패 | 계산 열에 값(`NULL` 포함)을 지정 | 그 열을 INSERT 열 목록에서 뺀다 |
| 위험계좌 칸이 `UNAVAILABLE` | 활성 위험 데이터셋이 없음(`ACTIVE_DATASET_MISSING`) | §6 시드 적용 |
| 기대한 이체가 보류되지 않고 실행됨 | 같은 수취계좌로 간 성공 이체가 10분 창에 3건 미만이거나 창 밖이었다(11/17에는 600,000원 한 건만 그 계좌로 갔다. 망설임은 정상 기록됐다) | 해당 계좌로 간 이체의 시각 · 건수와 행동 기록(`fds_behavior_event`의 `STEP_BACK` · `AMOUNT_EDITED`)을 조회해서 원인을 가른다 |
| 정책 B를 예상했는데 A로 걸림 | A가 먼저 판정되고, 10분 창이 앞선 이체(망설임 포함)를 포함함 | 이체 시각을 나열해 창에 들어오는 건을 센다 |
| 권한 부여 호출이 `CMN0002` | `Idempotency-Key` 헤더 누락(**회수 호출에도 필요**) | 도구 스크립트 사용 |
| 에이전트 로그의 `WARNING: Unsupported upgrade request.` | 서버 HTTP 클라이언트의 HTTP/2 업그레이드 시도로 추정(요청은 200). 원인은 코드로 확인하지 않았다 | 무해로 판단 |

## 10. 끄기와 되돌리기

- **Agent만 중단**: `sudo docker stop corebank-fds-agent`. P6 인계서(§10)에 따르면 서버는 계속 동작하고, 정책 보류는 그대로 걸리며 점수 · 조사 호출은 실패하거나 재시도한다. 보류 이체는 `HELD`로 남고 자동 승인 · 거절되지 않는다. 3회 실패한 조사는 `FAILED`가 되어 Agent를 다시 켜도 자동 재개되지 않는다(서버 코드로 일부 확인했고 11/17에 Agent를 끈 시험은 하지 않았다).
- **기능 스위치**(`app.env`, 서버 컨테이너를 다시 만들어야 반영): `APP_FDS_POLICY_ENABLED`(보류 판정), `APP_FDS_SCORE_ENABLED`(행동 점수), `APP_FDS_INVESTIGATION_ENABLED`(조사). 이미 `HELD` · `PENDING` · `FAILED`인 데이터는 지워지거나 취소되지 않는다.
- **서버 이전 컨테이너로 복귀**: [릴리스 런북](vm_release_agent_runbook.md) §2-3의 되돌리기 명령.
- **시드는 되돌리는 SQL을 만들지 않았다.** 되돌리려면 적용 직전에 받은 운영 백업(`…-before-fds-seed.sql.gz`)에서 복원해야 해서, 시연 뒤 정리는 해당 계정 비활성화로 한다(비활성화 방법은 P6와 정한다).
- **시연 뒤 정리 대상**: 부스 계정 20 · 위험계좌 2 · 예비 6명(같은 비밀번호, 큰 잔액, 외부에 열린 서버), 시험으로 등록된 `fds_risk_account`의 `ADMIN_CONFIRMED` 행과 `fds_risk_registration` · 근거 기록, 보관 컨테이너, VM의 소스 · 시드 SQL · `admin_perm.sh`.

## 11. 확인하지 못한 것

- Agent CPU · 메모리 실측, 외부에서 8000 포트 접속을 시도하는 시험(`ss`로 loopback 바인딩만 확인), 이미지 내부 `.py` 파일별 해시 대조(P6 인계서 §11).
- 시연 송금 계정으로 이체한 흐름(시험 계정으로만 확인).
- 릴리스 8 교체 뒤 이의제기 분석 전 과정 재시험(워커 `claim` 호출이 성공 응답을 받는 것까지 확인).
- 시연 뒤 정리 방법(비활성화 · 삭제)의 실제 실행.
