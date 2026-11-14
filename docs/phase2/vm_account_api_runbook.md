# VM 계정계 API 기동 · 정지 런북 (#849)

> 11/18 시연과 [DR 전환](ph54_onprem_dr_runbook.md) 때 온프레미스 VM(`192.168.0.211`)에서 계정계 API를 켜고 끄는 절차다.
> 승격과 복제 정지는 P2([DR 런북](ph54_onprem_dr_runbook.md) §6)가 하고, 이 문서는 그 절차가 부르는 **기동 · 정지 한 칸**만 다룬다.
> 작성 2026-11-10, 2026-11-13 갱신: 승격(14:16:40) 뒤 승격 DB 위 기동, 계좌 업무 확인. **2026-11-14 갱신: 외부 인터넷에서 닿는 경로를 Cloudflare 터널로 열고(§8) 집에서 로그인 · 이체 · 거래내역 조회를 확인했다.** 확인한 것과 하지 않은 것을 §7에 나눠 적는다.

## 1. 전제

- 이미지는 **릴리스 4 `corebank/corebank-server:e6dfafe4dfe64bb3fb94a82382e7a93d72ed3284`로 고정**한다(시험한 것과 시연하는 것을 같게 하려는 것이다). VM에 이미 받아 두었다.
- **실시간 레플리카에는 승격 전에 API를 붙이지 않는다.** 앱은 기동할 때 Flyway와 세션 저장소가 DB에 쓰는데, 읽기 전용 레플리카에 붙으면 실패하고 쓰기가 되는 상태로 붙이면 RDS와 GTID가 갈라진다. 승격 전 시험은 쓰기가 되는 격리 DB(`corebank-drtest-mysql`)에서만 한다.
- 복제 중인 VM은 재부팅과 도커 재시작을 하지 않는다. 새 컨테이너를 띄우는 것은 도커 재시작이 아니다.
- 환경 파일은 값을 이 문서와 저장소, 채팅, 이슈에 쓰지 않는다.

## 2. 환경 파일

시험용은 `/etc/corebank-drtest/app.env`(권한 600, root). 변수 **이름**은 아래와 같다.

| 변수 | 비고 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_HOST` | 격리 DB는 컨테이너 이름, 승격 뒤는 `127.0.0.1`(§5) |
| `DB_USER` `DB_PASSWORD` `FLYWAY_USER` `FLYWAY_PASSWORD` | 승격 DB에는 앱 계정이 **없다**(복제는 `minicore` 데이터만 가져오고 계정은 가져오지 않는다). 승격 뒤 VM에서 새로 만든다(§5-1) |
| `DB_POOL_SIZE` | 시험은 3 |
| `APP_OTP_CODE_HASH_ALGORITHM` `APP_OTP_CODE_HASH_KEY_BASE64` | 키는 VM에서 직접 생성한다 |
| `APP_LOGIN_ID_HMAC_KEY_BASE64` | 릴리스 4부터 필수. 없으면 기동하지 않는다. 용도와 재사용 기준은 아래 「로그인 아이디 키」 |
| `APP_AUDIT_ARCHIVE_STORAGE` | `local`. 감사 로그를 AWS 보관함이 아니라 서버 안에 저장한다 |
| `APP_LEDGER_RECONCILIATION_FULL_SCAN_CHUNK_TIMEOUT_SECONDS` | `1800`. 코드 기본값은 60초다 |
| `APP_INFO_API_BASE_URL` `APP_INFO_API_SECRET` | VM 정보계 API(`http://192.168.0.211:8081`)와 그 비밀. 비밀은 `info-api` 컨테이너의 `INFO_API_SECRET`과 같은 값이고 화면에 내지 않고 옮겼다. 호출 자체는 시험 전이다 |
| `CORS_ALLOWED_ORIGINS` | `https://www.corebank.cloud`(화면 출처). 비우면 허용 출처가 없어 화면에서 API 호출이 막힌다(`application-prod.yml`). Nginx는 CORS 헤더를 덧붙이지 않으므로 이 값이 유일한 설정이다(§8) |

기동할 때 덧붙이는 값(환경 파일에 넣지 않는다): `APP_INTERBANK_HOST=192.168.0.211`(VM 모의 대외기관, 포트 9300), 승격 뒤에는 `SERVER_ADDRESS=127.0.0.1`과 `APP_METRICS_ALLOWED_IP`(§5).

DB 접속 URL은 포트가 `3306`으로 고정이고 `sslMode=REQUIRED`다. 레플리카는 TLS 접속을 받는다(`--ssl-mode=REQUIRED`로 확인, `TLSv1.2,TLSv1.3`).

**로그인 아이디 키 (`APP_LOGIN_ID_HMAC_KEY_BASE64`)**
- 코드(`LoginIdBlindIndex`)에서 이 키는 **미가입 아이디의 로그인 실패 횟수를 아이디 원문 없이 찾는 검색 해시**에만 쓰인다(11/10 `dev` 기준 쓰는 곳은 `UnregisteredLoginFailurePersistenceAdapter` 하나). 가입 계정의 로그인 판정에는 쓰이지 않는다. 같은 아이디가 항상 같은 값이어야 해서 비밀키 HMAC을 쓴다.
- 키가 달라지면 이미 쌓인 미가입 아이디 실패 횟수는 다른 키로 만든 해시라 찾아지지 않고 **새로 시작**한다.
- **재사용 기준**: ① 승격 DB에 쌓인 실패 횟수를 이어가야 하는 **운영 전환**이면 AWS와 같은 키(Parameter Store `/corebank/login-id-hmac-key`, P1이 등록한 값)를 쓴다. 값은 화면 · 채팅에 내지 않고 VM에서 환경 파일로 옮기며, 한 번 정하면 바꾸지 않는다. ② **11/18 시연**은 연속성이 필요 없어 VM에서 새로 만든 키(11/9)를 쓴다. 현재 VM 환경 파일은 ②다. 11/11 P1이 새 키로 가면 된다고 확인했다(가입 계정 로그인에는 영향이 없고, AWS에 쌓인 미가입 아이디 실패 기록은 이어지지 않고 새로 센다). **승격 뒤에는 이 키를 바꾸지 않는다**(바꾸면 그때까지 쌓인 실패 횟수가 0부터 다시 시작한다).
- OTP 키(`APP_OTP_CODE_HASH_KEY_BASE64`)는 다르다. OTP는 180초 만료라 환경마다 달라도 되고 VM에서 따로 만든다.

## 3. 격리 DB 위 기동 (승격 전 시험)

```bash
sudo docker run -d --name corebank-drtest-api --network corebank-drtest-net --cpus 1 --memory 2g \
  -p 127.0.0.1:8080:8080 --env-file /etc/corebank-drtest/app.env \
  -e APP_INTERBANK_HOST=192.168.0.211 \
  corebank/corebank-server:e6dfafe4dfe64bb3fb94a82382e7a93d72ed3284
```

- 앱은 `/api/v1` 아래에 있고, 기동까지 약 20~60초 걸린다.
- 이 컨테이너를 밤에 켜 두지 않는다. 23:30 COB와 00:30 감사 적재가 격리 DB에서 돌 수 있다. 시험 때는 `sudo systemd-run --on-active=90m --unit=drtest-api-autostop docker stop corebank-drtest-api`로 자동 종료를 걸어 두었다.

## 4. 헬스체크와 정지

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/v1/actuator/health   # 200
sudo docker logs corebank-drtest-api 2>&1 | grep -E "ERROR|환경변수가 없습니다|APPLICATION FAILED" | cut -c1-200 | head
```

```bash
sudo docker stop corebank-drtest-api && sudo docker rm corebank-drtest-api
```

- 환경 파일, 이미지, DB는 지우지 않는다. 값을 바꾼 뒤에는 `restart`가 아니라 지우고 다시 만든다(`docker restart`는 환경 파일을 다시 읽지 않는다).

## 5. 승격 뒤 기동 (2026-11-13 실행)

레플리카는 기본 `bridge` 네트워크에서 호스트 `127.0.0.1:3306`으로만 열려 있고, 정보계 API(`info-api`)가 이미 호스트 네트워크로 붙는다. 계정계 API도 같은 방식으로 붙인다. 레플리카 컨테이너는 건드리지 않는다.

### 5-1. DB 계정 만들기 (승격 직후, 읽기 전용 해제 뒤)

승격 DB(`info-replica-mysql`)의 `mysql.user`에는 `etl_replica_reader`와 `root`만 있고 **`app_user` · `flyway_user`가 없다.** 복제는 `minicore` 데이터만 가져오고 계정(`mysql` 스키마)은 가져오지 않는다. 그래서 승격 직후 VM에서 새로 만든다. 비밀번호는 이 DB에서만 쓰이므로 RDS 값과 같을 필요가 없고, 무작위로 만들어 변수에만 담았다가 환경 파일에 넣는다(화면 · 채팅 · 이슈에 남기지 않는다).

```bash
APP_PW=$(openssl rand -hex 16); FLY_PW=$(openssl rand -hex 16)      # 출력하지 않는다
A1="SET sql_log_bin=0; CREATE USER 'app_user'@'%' IDENTIFIED BY '$APP_PW' REQUIRE SSL; GRANT SELECT, INSERT, UPDATE, DELETE ON minicore.* TO 'app_user'@'%'"
A2="SET sql_log_bin=0; CREATE USER 'flyway_user'@'%' IDENTIFIED BY '$FLY_PW' REQUIRE SSL; GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON minicore.* TO 'flyway_user'@'%'"
echo "$A1" | sudo bash -c 'MYSQL_PWD=$(cat /etc/info-etl/replica-root.pw) docker exec -i -e MYSQL_PWD info-replica-mysql mysql -uroot'
echo "$A2" | sudo bash -c 'MYSQL_PWD=$(cat /etc/info-etl/replica-root.pw) docker exec -i -e MYSQL_PWD info-replica-mysql mysql -uroot'
```

- **이 블록은 한 번만 실행한다.** 계정이 이미 있으면 `CREATE USER`는 실패하는데 비밀번호 변수는 새로 만들어져, 뒤의 환경 파일 값과 DB 계정의 비밀번호가 어긋나 API가 DB에 붙지 못한다. 다시 실행하기 전에 `SELECT user, host FROM mysql.user`로 계정이 없는지 확인한다(`flyway_user` 단계만 실패했다면 그 줄만 따로 실행하고 `FLY_PW`만 새로 만든다).
- 두 계정 모두 `REQUIRE SSL`이다(앱 접속이 `sslMode=REQUIRED`). Host는 기존 계정(`etl_replica_reader`, `root`)처럼 `%`로 만들었다.
- `sql_log_bin=0`으로 만들어 복제 기록(GTID)에 남기지 않았고, 생성 직후 `gtid_executed`가 승격 시점 값(`...:1-91316`) 그대로임을 확인했다.
- `app_user`는 데이터 읽기 · 쓰기만, `flyway_user`는 릴리스 6의 마이그레이션(CREATE · ALTER · DROP · INDEX · REFERENCES와 `common_code` INSERT)에 필요한 권한이다. 릴리스 6을 적용하기 전에 권한을 한 번 더 확인한다(#989).

### 5-2. 환경 파일 만들기 (`/etc/corebank/app.env`)

시험용 파일(`/etc/corebank-drtest/app.env`)의 키 변수(OTP · 로그인 ID · 정보계 등)는 그대로 쓰고, DB와 스위치만 바꾼다. 폴더 `700`, 파일 `600`, root 소유이고 값은 화면에 내지 않는다.

```bash
sudo mkdir -p /etc/corebank && sudo chmod 700 /etc/corebank && sudo cp /etc/corebank-drtest/app.env /etc/corebank/app.env && sudo chmod 600 /etc/corebank/app.env
sudo sed -i '/^DB_HOST=/d;/^DB_USER=/d;/^DB_PASSWORD=/d;/^FLYWAY_USER=/d;/^FLYWAY_PASSWORD=/d;/^DB_POOL_SIZE=/d;/^APP_SUBSCRIPTION_EARLYTERMINATION_ENABLED=/d' /etc/corebank/app.env
printf 'DB_HOST=127.0.0.1\nDB_USER=app_user\nDB_PASSWORD=%s\nFLYWAY_USER=flyway_user\nFLYWAY_PASSWORD=%s\nDB_POOL_SIZE=10\nAPP_SUBSCRIPTION_EARLYTERMINATION_ENABLED=true\n' "$APP_PW" "$FLY_PW" | sudo tee -a /etc/corebank/app.env >/dev/null
echo 'CORS_ALLOWED_ORIGINS=https://www.corebank.cloud' | sudo tee -a /etc/corebank/app.env >/dev/null
sudo cut -d= -f1 /etc/corebank/app.env | sort | tr '\n' ' '      # 변수 이름만 확인
```

- 시험용 파일을 통째로 복사하므로 **시드 실행 플래그(`APP_PHASE2_SEED_*`)가 섞여 있으면 지운다**(`sudo sed -i '/^APP_PHASE2_SEED_/d' /etc/corebank/app.env`). `phase2-seed` 프로필에서는 최소 시드 플래그 하나만 있어도 가드가 막지 않아 기동 때 승격 DB에 시드가 적재될 수 있다. 11/13 14:23 기동 뒤 11/14 15:42에 시연 계정이 들어오기 전까지 새 고객이 생기지 않았으므로 이번에는 적재되지 않았다.

### 5-3. 기동

```bash
sudo mkdir -p /var/lib/corebank-audit && sudo chmod 700 /var/lib/corebank-audit
sudo docker run -d --name corebank-server --network host --cpus 1 --memory 2g \
  --env-file /etc/corebank/app.env \
  -v /var/lib/corebank-audit:/audit -e APP_AUDIT_ARCHIVE_LOCAL_DIR=/audit \
  -e SERVER_ADDRESS=127.0.0.1 -e APP_INTERBANK_HOST=192.168.0.211 -e APP_METRICS_ALLOWED_IP=127.0.0.1 \
  -e SPRING_PROFILES_ACTIVE=prod,phase2-seed \
  corebank/corebank-server:e6dfafe4dfe64bb3fb94a82382e7a93d72ed3284
```

- `DB_HOST=127.0.0.1`, DB 계정은 §5-1에서 만든 `app_user` · `flyway_user`다. 11/13 14:23에 이 명령으로 기동했고 헬스 200, `Started CorebankApplication` 약 25초였다.
- **첫 기동은 `-e SPRING_PROFILES_ACTIVE=prod,phase2-seed`로 했다.** 스케줄러(COB 23:30, 일일 이체 배치)를 끄려는 것이었다(승격 DB의 영업일이 2026-11-12에서 멈춰 있었다). **그러나 이 프로필로도 스케줄러가 돌았다(2026-11-14 확인).** 로그상 활성 프로필은 `prod`, `phase2-seed`인데, 11/13 23:30에 이 서버가 COB를 실행해 영업일이 11/12 → 11/13이 됐고(`business_date_cutoff` 11/12 `CLOSED`, `ledger-reconciliation` · `account-maturity` `SUCCESS`), 11/14 00:10~00:14에 일일 이체 배치가 385건을 만들었다. 스케줄링이 켜지는 경로는 확인하지 못했다. **이 프로필을 스케줄러 차단 수단으로 믿지 않는다.** **스케줄러를 켠 채 운영하기로 정했다(2026-11-14).**
- `SERVER_ADDRESS=127.0.0.1`로 VM 안에서만 받게 한다. 호스트 네트워크로 띄우면 이 값이 없을 때 교육장 전체에 열린다.
- `APP_METRICS_ALLOWED_IP=127.0.0.1`은 Prometheus가 호스트에서 `127.0.0.1:8080`을 부른다는 가정이다. 기본값 `192.168.0.211`은 VM 안의 요청에서 거부된다(§7).
- 승격 전 시험 때와 달리 `--network host`라 `-p`를 쓰지 않는다.

**헬스체크 · 로그 · 정지 (승격 뒤 실제 컨테이너 `corebank-server`)**

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/v1/actuator/health   # 200
sudo docker logs corebank-server 2>&1 | grep -E "ERROR|환경변수가 없습니다|APPLICATION FAILED" | cut -c1-200 | head
sudo docker stop corebank-server && sudo docker rm corebank-server
```

- 호스트 네트워크라 포트 매핑이 없고 8080은 VM 안에서만 열린다(`SERVER_ADDRESS`). 기동까지 약 20~60초 걸린다.
- 정지해도 환경 파일 · 이미지 · 감사 보관 디렉터리(`/var/lib/corebank-audit`)는 지우지 않는다. 값을 바꾼 뒤에는 `restart`가 아니라 지우고 다시 만든다.
- 자동 재기동(`--restart`)은 붙이지 않았다. VM이 재부팅된 뒤 의도하지 않게 COB(23:30)나 감사 적재(00:30)가 도는 것을 피하려는 것이다. 이 선택은 시연 운영 방식과 함께 팀과 정한다.
- 정지 전에 승격 DB에 대한 쓰기가 끝났는지(진행 중인 이체 · COB 없음)를 먼저 확인한다. 이 확인 절차는 P2의 [DR 런북](ph54_onprem_dr_runbook.md) §6을 따른다.

**감사 로그 로컬 보관 (`APP_AUDIT_ARCHIVE_STORAGE=local`)**
- 기본 보관 경로는 컨테이너 안 `/app/build/audit-archive`(`Dockerfile`의 `WORKDIR /app`, 설정 `app.audit.archive.local-dir`의 기본값 `build/audit-archive`)라 **컨테이너를 지우면 보관 파일도 사라진다.** 그래서 VM 디렉터리 `/var/lib/corebank-audit`를 `/audit`로 연결하고 `APP_AUDIT_ARCHIVE_LOCAL_DIR=/audit`를 준다. 컨테이너가 root로 실행되므로(`Dockerfile`에 `USER` 없음) 별도 권한 조정은 필요 없을 것으로 본다. **시험 전이다.**
- 매일 01:00 감사 무결성 검사가 같은 저장소를 읽으므로, 보관 파일이 사라지면 DB 기록과 어긋나는 것으로 보일 수 있다.
- **한계**: 로컬 보관은 S3 Object Lock(WORM)과 달리 VM에 접근할 수 있는 사람이 지우거나 고칠 수 있다. 시연 환경은 원본 감사 기록이 DB에 남고 AWS 보관함 적재는 일부러 하지 않는다. 운영 전환으로 쓰려면 S3 적재(WORM)를 되살리거나 별도 보존 대책이 필요하며, 이는 팀 결정이다.

## 6. 전수 대사 리허설 (PH-38 실행기)

격리 DB에서 전수 대사를 돌려 시간과 불일치 수를 로그에 남기고 스스로 종료한다. `phase2-seed` 프로필을 붙이고 시드 실행 플래그는 켜지 않는다(이 프로필이 스케줄러를 확실히 막지는 않는다, §5).

```bash
sudo docker run -d --name corebank-drtest-recon --network corebank-drtest-net --cpus 1 --memory 2g \
  --env-file /etc/corebank-drtest/app.env \
  -e SPRING_PROFILES_ACTIVE=prod,ph38-baseline,phase2-seed \
  -e APP_PH38_BASELINE_EXECUTE=true -e APP_PH38_BASELINE_ROUNDS=2 -e APP_PH38_BASELINE_WARMUP_ROUNDS=1 \
  corebank/corebank-server:e6dfafe4dfe64bb3fb94a82382e7a93d72ed3284
```

- 끝나면 `sudo docker inspect -f '{{.State.Status}} exit={{.State.ExitCode}}' corebank-drtest-recon`이 `exited exit=0`이어야 한다. `--rm`을 쓰지 않는다. 실행 전후로 `batch_execution_lock`의 `currently_running`이 모두 `0`인지 본다.
- 11/10 결과: 불일치 0건, 예열 회차 559초(캐시가 비어 있을 때), 측정 회차 25초. 승격 직후 처음 도는 대사는 10분 가까이 걸릴 수 있다.
- 주의: 이 프로필에서도 격리 DB의 `IDEMPOTENCY_KEY_CLEANUP` 락이 갱신됐다. 원인은 확인하지 못했으니 이 프로필로 COB를 막는다고 믿지 않는다.

## 7. 확인한 것과 하지 않은 것

**확인함 (2026-11-13, 승격 DB)**
- 승격 DB(`info-replica-mysql`, 읽기 전용 해제 14:16:40)에 릴리스 4 API를 기동(§5): 헬스 200, Flyway 검증 통과(마지막 버전 `202611082006`), 새 앱 계정으로 TLS 접속.
- 비공개 경로(`127.0.0.1:8080`, 시드 고객 `ph60_user_00001`): 로그인 · 계좌 비밀번호 검증 · OTP · 당행 이체(1,111원), 타행이체(1,111원, VM 모의 서버 경유, 받는 은행 `004`), 상품가입(코어 정기예금 10만 원 12개월), 중도해지(원금 전액 반환, 가입 당일이라 이자 0). 당행 이체는 DB에서 원장 짝과 잔액이 맞는지 확인했다.
- 상품가입은 필수 약관을 먼저 열람(`GET /products/{productId}/terms/{termsId}`)해야 하고, 열람 이력이 없으면 `PRD0005`로 거절된다(규칙대로 동작).
- 공개 경로(§8): 11/13에는 사설 IP라 교육장 안에서만 로그인 · 이체가 됐다(15:03, 1,234원). **11/14에 Cloudflare 터널로 바꾼 뒤 교육장 밖(집)에서 `www.corebank.cloud` 로그인, 이체(1,000원), 거래내역 조회가 성공했다**(18:35). 로그인 기록의 접속 IP도 집 공인 IP로 남는다. 이 이체의 원장 2행(출금 · 입금), GL 전표(차변 = 대변), 두 계좌의 잔액과 마지막 원장 잔액이 일치하고 11/13 이후 차변 ≠ 대변 전표는 0건이다.
- 스케줄러: 승격 뒤 서버가 COB와 일일 이체 배치를 실제로 실행했다(§5).
- Nginx를 거친 `/api/v1/internal/`과 `/api/v1/actuator/prometheus`는 404, 건강 확인은 통과, CORS 사전 요청의 허용 헤더는 중복 없이 한 번씩 나온다.
- 로그인 아이디 키는 시연에서 VM에서 새로 만든 키를 쓴다(11/11 P1 확인, §2).

**확인함 (2026-11-10, 격리 DB)**
- 릴리스 4 이미지가 기동하고 헬스 200, 로그인 키 · OTP 키 관련 오류 없음.
- `APP_INTERBANK_HOST=192.168.0.211`로 기동, 컨테이너에서 `192.168.0.211:9300` · `:8081`에 접속됨.
- 레플리카 TLS 접속(`sslMode=REQUIRED` 조건).
- VM 안에서 지표 경로를 부르면 `401`로 거부됐다(컨테이너 안의 앱은 호스트 요청을 도커 게이트웨이 주소로 본다). 당시 부른 경로가 `/actuator/prometheus`인지 `/api/v1/actuator/prometheus`인지 기록이 없어, 이 줄은 실제 `/api/v1/actuator/prometheus` 접근통제 검증으로 보지 않는다. 그 경로의 외부 차단과 허용 IP 검증은 아래 「하지 않음」에서 시험한다.
- 전수 대사 리허설(§6).
- 접속 경로: 인터넷의 `www.corebank.cloud` 화면이 VM의 Nginx(`192.168.0.211:443`)를 불러 `200 OK`(§8).

**하지 않음**
- 감사 로컬 보관 볼륨(§5)이 컨테이너를 지우고 다시 만든 뒤에도 파일을 유지하는지, 01:00 무결성 검사가 로컬 파일로 통과하는지.
- 정보계 API 호출. 환경 파일에 `APP_INFO_API_BASE_URL` · `APP_INFO_API_SECRET`이 시험용에서 복사돼 있지만 호출은 시험하지 않았다.
- `APP_METRICS_ALLOWED_IP=127.0.0.1`로 VM 안에서 `/api/v1/actuator/prometheus`가 200이 되는지(Nginx를 거친 외부 요청이 404로 막히는 것은 확인함).
- 화면에서의 상품가입 · 중도해지(API로는 확인했다).
- 11/14 밤 이후 COB의 영업일 이동(영업일이 달력보다 앞서면 COB를 건너뛰는 규칙이 시연일 11/18에 영업일을 맞추는지), 만기 해지, 자동 · 예약이체 개별 시험.
- 릴리스 6 적용(격리 DB 시험, 백업, 마이그레이션)과 Agent 두 개(#989).
- 부스처럼 여러 명이 동시에 접속할 때의 VM 용량.

## 8. 접속 경로 (시연용, 2026-11-14 Cloudflare 터널)

**한 줄 구조**: 사용자 → Cloudflare(`www`는 Pages, `api`는 터널) → VM의 `cloudflared` → VM의 Nginx(`192.168.0.211:443`) → 계정계 API(`127.0.0.1:8080`). VM은 사설 IP이지만 `cloudflared`가 VM에서 Cloudflare로 **바깥으로** 연결하므로 외부 인터넷에서도 `api.corebank.cloud`가 열린다. 교육장 공인 IP의 80 · 443은 다른 서비스가 쓰고 있어 VM으로 오는 길이 없었다. 11/13에는 `api`를 사설 IP(`A 192.168.0.211`)로 바꿔 교육장 안에서만 닿았고, 11/14에 아래로 바꿨다.

**DNS**: 도메인 `corebank.cloud`의 네임서버를 가비아에서 Cloudflare(`ollie.ns.cloudflare.com`, `yadiel.ns.cloudflare.com`)로 옮겼다(11/14 18:27 반영, DNSSEC는 꺼져 있었다). 레코드는 Cloudflare에서 관리한다: `www` → `corebank-fe.pages.dev`(Pages 커스텀 도메인), `api` → 터널(자동 생성). 옛 `A 192.168.0.211`은 지웠다. 가비아에 남은 ACM 검증 CNAME 2개와 `_acme-challenge.api` TXT는 더 쓰지 않는다. 영역과 Pages는 같은 Cloudflare 계정에 있어야 한다(다른 계정이면 CNAME이 막힌다).

**터널 (`corebank-api`)**: Public Hostname `api.corebank.cloud` → `https://192.168.0.211:443`, 원본 서버 이름 `api.corebank.cloud`. VM에는 `cloudflared`를 systemd 서비스로 설치했고(`sudo systemctl is-active cloudflared`, 로그의 `Registered tunnel connection`이 4개) 토큰은 서비스 설정에만 있다. **토큰은 문서 · 채팅 · 이슈에 쓰지 않고, `systemctl status` 출력에는 토큰이 보이니 공유하기 전에 가린다.** 시작 직후 `No ingress rules were defined` 경고가 한 줄 나오는 것은 Cloudflare에서 설정을 받기 전의 정상 메시지다(바로 뒤에 `Updated to new configuration`이 나온다).

**VM의 Nginx** (`nginx:1.27-alpine`, 컨테이너 `demo-nginx`, `--network host`, `--restart unless-stopped`): 설정 `/etc/corebank-demo/nginx.conf`, 인증서 `/etc/corebank-demo/ssl/{cert,key}.pem`(`key.pem`은 `600`, 개인 키는 VM 밖으로 내보내지 않는다).
```nginx
events {}
http {
  server_tokens off;
  client_max_body_size 45m;
  server {
    listen 192.168.0.211:443 ssl;
    server_name api.corebank.cloud;
    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    set_real_ip_from 192.168.0.211;
    real_ip_header CF-Connecting-IP;
    add_header Access-Control-Allow-Private-Network "true" always;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 120s;
    location ^~ /api/v1/internal/ { return 404; }
    location = /api/v1/actuator/health { proxy_pass http://127.0.0.1:8080; }
    location ^~ /api/v1/actuator { return 404; }
    location / { proxy_pass http://127.0.0.1:8080; }
  }
  server {
    listen 192.168.0.211:80 default_server;
    server_name _;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto https;
    proxy_read_timeout 120s;
    location ^~ /api/v1/internal/ { return 404; }
    location = /api/v1/actuator/health { proxy_pass http://127.0.0.1:8080; }
    location ^~ /api/v1/actuator { return 404; }
    location / { proxy_pass http://127.0.0.1:8080; }
  }
}
```
```bash
sudo docker run -d --name demo-nginx --network host --restart unless-stopped \
  -v /etc/corebank-demo/nginx.conf:/etc/nginx/nginx.conf:ro -v /etc/corebank-demo/ssl:/etc/nginx/ssl:ro nginx:1.27-alpine
```
- 호스트 네트워크라 `-p`를 쓰지 않고 사설 IP의 443과 80만 받는다(`ss -ltnp | grep -E ':80 |:443 '`). 계정계 API가 `127.0.0.1:8080`에만 열려 있어 `bridge` 컨테이너에서는 닿지 않으므로 호스트 네트워크가 필요하다.
- **접속자 IP**: `cloudflared`가 VM 안에서 Nginx에 연결하므로 Nginx가 보는 원격 주소는 늘 `192.168.0.211`이다. 처음에는 `X-Forwarded-For`가 이 값으로 덮여 로그인 기록과 감사 로그에 모든 접속자가 `192.168.0.211`로 남았다. `set_real_ip_from`(`cloudflared`의 연결 주소만 신뢰)과 `real_ip_header CF-Connecting-IP`를 넣어 실제 접속자 IP가 남는다(집 공인 IP로 확인).
- CORS는 API(`CORS_ALLOWED_ORIGINS`)가 응답하고 Nginx는 덧붙이지 않는다(같은 헤더가 둘이면 브라우저가 거부한다). `Access-Control-Allow-Private-Network: true`는 사설 IP로 부를 때 크롬이 요구하던 헤더라 지금은 필요 없지만 남겨 두었다(앱은 이 헤더를 만들지 않는다).
- 운영 프로필의 세션 쿠키가 `Secure`라 HTTPS가 아니면 로그인이 유지되지 않는다. 앱은 `forward-headers-strategy: native`라 `X-Forwarded-Proto`를 넘겨야 한다.
- `/api/v1/internal/`(이의제기 Agent 내부 API)과 `/api/v1/actuator`(health 제외)는 외부에 막는다(외부에서 404 확인). 업로드 한도 45MB는 이의제기 첨부(앱 한도 40MB) 때문이다.
- 설정을 바꾼 뒤에는 `sudo docker exec demo-nginx nginx -t && sudo docker exec demo-nginx nginx -s reload`로 다시 읽힌다(바인드 마운트한 파일은 `sed -i`로 바꾸면 도커가 연결을 놓치니 같은 파일을 덮어쓴다).

**80번 경로(예비)**: 교육장이 제공한 도메인 `corebank.shinhanacademy.co.kr`은 외부 → HTTPS(443) → VM의 80번으로 이어진다. 위 80번 서버 블록이 이를 받는다(11/13 외부에서 health 200, `/internal/` 404 확인). 이 도메인은 `corebank.cloud`와 다른 사이트라 화면의 로그인 쿠키가 따라가지 않아 화면용 경로로는 쓰지 않는다. 터널이 막혔을 때의 예비다.

**인증서 (Let's Encrypt)**: 브라우저가 보는 인증서는 Cloudflare가 발급한 것이고, VM의 인증서는 `cloudflared`가 원본 서버 이름 `api.corebank.cloud`로 Nginx에 연결할 때 쓰인다. 2026-11-13 발급, 만료 **2027-02-11**, DNS-01 수동 방식이라 자동 갱신되지 않는다. 갱신이 필요하면 `_acme-challenge.api` TXT를 이제는 **Cloudflare**에 추가한다.
```bash
sudo certbot certonly --manual --preferred-challenges dns -d api.corebank.cloud --agree-tos --register-unsafely-without-email
sudo cp /etc/letsencrypt/live/api.corebank.cloud/fullchain.pem /etc/corebank-demo/ssl/cert.pem
sudo cp /etc/letsencrypt/live/api.corebank.cloud/privkey.pem /etc/corebank-demo/ssl/key.pem && sudo chmod 600 /etc/corebank-demo/ssl/key.pem
sudo docker exec demo-nginx nginx -s reload
```

**VM 접속**: VM은 AWS SSM 하이브리드 노드(`mi-0be0137569259c33f`)로 등록돼 있어 AWS 콘솔의 Systems Manager 세션으로 교육장 밖에서도 접속된다(세션 사용자 `ssm-user`, `sudo` 가능, 쓰기와 DB 접속 확인). 등록 자체는 수동으로 했지만 VM의 SSM Agent가 쓰는 IAM 역할(`${name_prefix}-onprem-ssm-role`)은 터라폼(`terraform/main`의 compute 모듈 `iam.tf`)이 관리한다. **`terraform/main`을 destroy하면 이 역할이 지워져 VM이 `ConnectionLost`가 되고 콘솔 세션이 열리지 않을 것으로 본다**(AWS가 실제로 어떻게 처리하는지는 시험하지 못했다). 철거 뒤에도 쓰려면 철거 전에 이 역할과 정책을 destroy 대상에서 빼야 하고(예: state에서 분리한 뒤 `terraform plan`으로 destroy 목록에서 빠지는지 확인), 공유 state라 팀과 조율한다. 가장 안전한 것은 철거 전에 VM 작업을 끝내 두는 것이다.

## 9. 관련 문서

- [PH-54 DR 런북](ph54_onprem_dr_runbook.md): 승격, 쓰기 차단, GTID 동기화
- [VPN · VM 런북](ph100_vpn_vm_runbook.md): 터널 복구와 복제 확인
- [모의 대외기관](mock_external_server.md): VM 9300 · 9301
