# PH-49c C 운영 성능 재측정 실행 가이드

이 문서는 P6이 **Windows Git Bash에서 k6를 실행**하고, 기존 운영 EC2의 SSM 셸과 MySQL CLI에서 측정 환경·정합성을 확인한 절차를 재현할 때 쓴다. 측정 목적과 비교 조건은 [PH-49c 계획](ph49c_jdbc_session.md#운영-성능-재측정-계획), 2026-10-26의 실제 시각·수치·판정은 [C 측정 결과](ph49c_jdbc_session_results.md)에 있다. 아래 명령의 `k6 run`은 운영 DB에 **실제 이체와 원장·전표를 생성**한다. 재측정 전에는 대상과 측정 창을 다시 확인한다.

## 1. 대상과 측정 창 확인

P4와 아래 항목을 확인하고 측정 조건으로 기록한다.

- **경로:** 기존 ALB → 앱 EC2 **1대** → 기존 RDS이고 새 환경으로 라우팅되지 않는다.
- **이미지·설정:** 릴리스 2의 `176fe574…` 이미지에 PH-49c(PR #641)와 풀 20 상향 되돌림(PR #733)이 포함됐는지 확인한다. 로그인 세션은 Spring Session JDBC이고 Hikari 메인 풀은 **10개**여야 한다. 일회성 저장소의 `provider=jdbc` 여부는 별도 조건이다.
- **DB 작업:** RDS 스냅샷·동기화·컷오버가 시작되지 않았고 P4가 해당 창을 허용했는지 확인한다.
- **동시 작업:** PH-11·PH-28·PH-38 등 다른 부하 측정과 겹치지 않는다. `:30`·`:45` 대사 작업과 23:20~00:20 배치 구간을 피한다.
- **B′ 비교:** 데이터 규모, `perf/k6/load.js` 버전, `MAX_VUS`·`SESSION_POOL`·`RATE`·`DURATION`과 인프라가 #593 B′와 얼마나 같은지 확인하고 차이를 결과에 기록한다.

P4에게 **연결 점검·예열을 포함한 전체 시작 5분 전**에 알리고, 종료 후에도 알린다. 하나의 예약된 창으로 공유했다면 요청률마다 다시 알릴 필요는 없다. P4와 정한 창을 벗어나면 남은 회차를 다른 창으로 옮긴다.

AWS 콘솔의 기존 ALB 리스너가 연결한 Target group에서 앱 EC2가 정확히 1대이며 `healthy`인지 확인한다. EC2 ID를 기록하고, 기존 RDS Endpoint와 앱의 `DB_HOST`가 같은지 아래 방식으로 대조한다. ALB 대상만 봐서는 DB 연결 대상을 알 수 없다.

기존 앱 EC2에 SSM으로 접속한다. `ssm-user`에게 Docker 소켓 권한이 없을 수 있으므로, 권한을 바꾸지 않고 `sudo -n`으로 읽기 전용 조회한다. Compose 작업 디렉터리로 이동할 필요는 없다.

```bash
hostname
sudo -n docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
sudo -n docker inspect \
  --format 'name={{.Name}} image={{.Config.Image}} imageId={{.Image}}' \
  corebank-server
sudo -n docker inspect \
  --format 'started_at={{.State.StartedAt}} image={{.Config.Image}}' \
  corebank-server
sudo -n docker inspect \
  --format 'entrypoint={{json .Config.Entrypoint}} cmd={{json .Config.Cmd}}' \
  corebank-server
sudo -n docker inspect \
  --format '{{range .Config.Env}}{{println .}}{{end}}' corebank-server |
  grep -E '^(SPRING_PROFILES_ACTIVE|SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE)='
sudo -n docker inspect \
  --format '{{range .Config.Env}}{{println .}}{{end}}' corebank-server |
  sed -n 's/^DB_HOST=//p'
```

마지막 출력은 RDS 콘솔의 **기존 DB Endpoint**와 대조한다. 환경변수 전체, Compose 파일 본문, DB 비밀번호·쿠키·토큰은 출력하거나 결과 문서에 붙이지 않는다. 2026-10-26 C 측정의 릴리스 2 이미지 태그는 `corebank/corebank-server:176fe574b6b5d2a1008ce9ce9359753b4d658cdb`였다. 사후 이미지 조회만으로 과거 실행 상태를 단정하지 말고, 같은 컨테이너의 `started_at`을 측정 첫 START와 비교한다. C의 `started_at`은 2026-10-24 02:32:15.706352567 UTC로 네 회차보다 앞서므로 그 사이 컨테이너 재시작은 없었다. 10/27 확인 당시 기존 ALB `corebank-alb`의 대상 그룹 `corebank-tg`에는 앱 EC2 `i-0c861fe2dc634f3af` 1대만 `healthy`로 등록돼 있었고, 컨테이너 `DB_HOST`는 `corebank-database`의 RDS Endpoint와 일치했다. 실행 프로필은 `prod,qa-seed`, 기동 명령은 `java -jar app.jar`(추가 인자 없음)였다. `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` 환경변수가 없고 실행 이미지의 `application-prod.yml`에 풀 10이 설정돼 있다. 이는 설정 근거이며 Hikari 객체의 실제 `maximumPoolSize`를 직접 조회한 기록은 아니다.

현재 SSM 디렉터리에서 `docker compose ps`가 Compose 파일 없음으로 실패해도 위 `docker inspect`는 실행할 수 있다. Compose 상태가 꼭 필요하고 배포 경로가 `/home/ubuntu/corebank`라면 `sudo -n docker compose -f /home/ubuntu/corebank/docker-compose.yml ps`로 확인한다. 해당 경로도 없다면 다른 EC2에 접속했거나 배포 경로가 바뀌었는지 P4와 확인한다. `sudo -n`이 권한 오류를 내면 권한을 임의로 바꾸지 않고 조회 권한을 확인한다.

PH-49c의 **로그인 세션 JDBC 저장**과 `app.ephemeral-store.provider`의 일회성 토큰 저장은 다른 설정이다. 테스트 계정으로 기존 ALB를 통해 로그인하고 기존 RDS에서 `SPRING_SESSION`에 새 행이 생기는지 확인한다. 세션 속성의 직렬화 데이터는 조회하지 않는다. MySQL CLI에서 로그인 전후 아래 건수와 생성 시각을 비교한다. `<테스트 user_id>`는 실제 값으로 바꾸고 외부에 공유하지 않는다.

```sql
USE minicore;
SELECT @@session.time_zone, NOW(), UTC_TIMESTAMP();
SELECT COUNT(*) AS active_sessions,
       FROM_UNIXTIME(MAX(s.CREATION_TIME) / 1000) AS newest_created_at
FROM customer c
JOIN SPRING_SESSION s ON s.PRINCIPAL_NAME = CAST(c.customer_id AS CHAR)
WHERE c.user_id = '<테스트 user_id>'
  AND s.EXPIRY_TIME > UNIX_TIMESTAMP(NOW(3)) * 1000;
```

로그인 후 보호 API가 같은 세션으로 동작하는지도 확인한다. 기존 세션이 이미 있다면 단순 건수보다 새 `CREATION_TIME`이 로그인 시각과 맞는지가 중요하다. 이 점검만으로 ALB·EC2·RDS 경로 전체가 증명되지는 않으므로 위 대상 그룹·Endpoint 확인과 함께 기록한다.

## 2. 부하 생성기 준비와 짧은 연결 점검

k6는 앱 EC2와 **다른 기기**에서 실행한다. P6의 Windows Git Bash에서는 저장소 루트(`perf/k6/load.js`가 보이는 폴더)를 사용한다. k6 설치가 필요하면 [Grafana의 Windows 설치 안내](https://grafana.com/docs/k6/latest/set-up/install-k6/)에 따라 `winget install k6 --source winget`을 실행한 뒤 Git Bash를 새로 열어 `k6 version`을 확인한다. 설치 방법과 버전은 재측정 시점에 기록한다.

```bash
cd ~/Intellij-workspace/corebank-server
ls perf/k6/load.js
k6 version
BASE_URL='https://api.corebank.cloud/api/v1'
RESULT_DIR="../ph49c-c-results-$(date '+%Y%m%d-%H%M%S')"
mkdir -p "$RESULT_DIR"
printf '대상=%s\n결과 폴더=%s\n' "$BASE_URL" "$RESULT_DIR"
```

`BASE_URL`이 **확인한 기존 ALB 경로**인지 검사한다. 위 `RESULT_DIR`은 저장소 밖에 만든다. 같은 Git Bash에서 변수·함수를 이어서 사용한다. 새 터미널을 열었다면 변수와 함수를 다시 정의한다. 결과 폴더에는 세션 정보·오류 응답이 들어갈 수 있으므로 원본 JSON·로그를 Git이나 공개 채널에 올리지 않는다.

짧은 연결 점검은 본측정과 분리해 저장한다. `setup()`에서 200개 고객 세션을 만들므로 20초 요청 구간보다 전체 실행 시간이 길 수 있다.

```bash
k6 run -e BASE_URL="$BASE_URL" -e RATE=1 -e DURATION=20s \
  -e MAX_VUS=200 -e SESSION_POOL=200 \
  --summary-trend-stats='avg,min,med,p(90),p(95),p(99),max' \
  --summary-export="$RESULT_DIR/check_summary.json" \
  perf/k6/load.js 2>&1 | tee "$RESULT_DIR/check.log"
status=${PIPESTATUS[0]}
printf 'check exit=%s\n' "$status"
```

`transfer_duration`, `transfer_success`, `iterations`가 출력됐는지, 인증 오류와 HTTP 실패가 없는지 확인한다. **k6 종료 코드 0만으로 개별 요청 성공을 뜻하지 않는다.** 점검 결과는 C의 4회 통계에 넣지 않는다.

## 3. 예열 1회

P6의 C 측정에서는 **5/s·3분 1회**를 예열로 분리했다. 예열도 실제 이체를 만들며 결과는 보존하되 본측정 통계에 넣지 않는다. 1단계에서 P4와 공유한 측정 창 안에서 실행한다.

```bash
printf '%s WARMUP START rate=5 duration=3m\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" |
  tee -a "$RESULT_DIR/times.log"
k6 run -e BASE_URL="$BASE_URL" -e RATE=5 -e DURATION=3m \
  -e MAX_VUS=200 -e SESSION_POOL=200 \
  --summary-trend-stats='avg,min,med,p(90),p(95),p(99),max' \
  --summary-export="$RESULT_DIR/warmup_rate5_summary.json" \
  perf/k6/load.js 2>&1 | tee "$RESULT_DIR/warmup_rate5.log"
status=${PIPESTATUS[0]}
printf '%s WARMUP END exit=%s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$status" |
  tee -a "$RESULT_DIR/times.log"
```

종료 코드와 인증·이체 실패를 확인한 뒤 본측정을 진행한다. 예열 조건이 달라졌다면 요청률·길이·결과 제외 여부를 결과 문서에 기록한다.

## 4. 본측정: 회차마다 1 → 2 → 3 → 5 → 10/s

각 수준은 **고정 도착률로 3분** 측정한다. `SESSION_POOL=MAX_VUS=200`이며, 로그인은 `setup()`에서 수준별 측정 구간 전에 수행한다. 본 구간은 계좌 비밀번호 확인 → OTP 발급 → OTP 확인 → 이체를 실행한다. `RATE`는 목표 시도율이고, 성공 이체 TPS는 별도로 계산한다.

아래 함수를 Git Bash에 한 번 정의한다. 함수 정의만으로는 부하가 발생하지 않는다. 결과 파일이 이미 있으면 중단해 덮어쓰지 않는다.

```bash
run_level() {
  local run="$1" rate="$2" name="c_run${1}_rate${2}" prefix
  prefix="$RESULT_DIR/$name"
  if [[ -e "${prefix}_summary.json" || -e "${prefix}_samples.json" || -e "${prefix}.log" ]]; then
    echo "기존 파일이 있어 중단: $prefix" >&2
    return 1
  fi
  printf '%s START %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$name" |
    tee -a "$RESULT_DIR/times.log"
  k6 run -e BASE_URL="$BASE_URL" -e RATE="$rate" -e DURATION=3m \
    -e SESSION_POOL=200 -e MAX_VUS=200 \
    --summary-trend-stats='avg,min,med,p(90),p(95),p(99),max' \
    --summary-export="${prefix}_summary.json" \
    --out "json=${prefix}_samples.json" \
    perf/k6/load.js 2>&1 | tee "${prefix}.log"
  local exit_code=${PIPESTATUS[0]}
  printf '%s END %s exit=%s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$name" "$exit_code" |
    tee -a "$RESULT_DIR/times.log"
  return "$exit_code"
}

run_round() {
  local run="$1" rate
  for rate in 1 2 3 5 10; do
    run_level "$run" "$rate" || return $?
  done
}
```

`run_level`이 수준별 **START·END 시각과 k6 종료 코드**를 `times.log`에 자동으로 적는다. `run_round`는 이를 1→2→3→5→10/s 순서로 호출하는 재실행용 묶음 함수다. **한 회차가 끝날 때마다 §5 정합성 검증을 마친 다음 다음 회차를 시작한다.** Git Bash에서 재측정할 때의 실행 순서는 다음과 같다. 2026-10-26의 개별 회차를 모두 이 함수로 실행했다는 뜻은 아니며, 당시 구간은 아래 `times.log` 기록값을 기준으로 한다.

1회차:

```bash
run_round 1
grep -E ' (START|END) c_run1_rate' "$RESULT_DIR/times.log"
```

1회차 정합성 검사 완료 후 2회차:

```bash
run_round 2
grep -E ' (START|END) c_run2_rate' "$RESULT_DIR/times.log"
```

2회차 정합성 검사 완료 후 3회차:

```bash
run_round 3
grep -E ' (START|END) c_run3_rate' "$RESULT_DIR/times.log"
```

3회차 정합성 검사 완료 후 4회차:

```bash
run_round 4
grep -E ' (START|END) c_run4_rate' "$RESULT_DIR/times.log"
```

`grep` 출력에서 해당 회차 **rate1의 START**와 **rate10의 END**를 §5의 `FROM_AT`·`TO_AT`으로 옮긴다. 출력에 다섯 수준의 END가 모두 있고 `exit=0`인지 확인한다. 4회차 뒤에도 정합성 검사를 한다. 이미 실행한 수준이 있으면 `run_round`를 다시 시작하지 말고 `times.log`와 결과 파일을 대조해 남은 수준만 `run_level <회차> <요청률>`로 실행한다. 10/s는 실패 요청이 발생해도 k6 프로세스가 `exit=0`으로 끝날 수 있다.

## 5. 회차별 정합성 SQL

본측정 **각 회차의 다섯 수준을 모두 마친 뒤** Git Bash의 `times.log`에서 확인한 첫 START부터 마지막 END까지를 검사 구간으로 쓴다. 요청률별로 다섯 번 검사하는 절차가 아니다. 아래는 2026-10-26에 기록된 실제 시각이다. 재측정 시에는 새 `times.log` 값을 쓴다.

| 회차 | `FROM_AT` (KST) | `TO_AT` (KST) | MySQL CLI에서 실행한 파일 |
|---|---|---|---|
| 1 | 2026-10-26 13:26:24 | 2026-10-26 13:50:49 | `/tmp/ph49c_verify_run1.sql` |
| 2 | 2026-10-26 14:49:43 | 2026-10-26 15:06:49 | `/tmp/ph49c_verify_run2.sql` |
| 3 | 2026-10-26 16:09:22 | 2026-10-26 16:26:29 | `/tmp/ph49c_verify_run3.sql` |
| 4 | 2026-10-26 16:49:26 | 2026-10-26 17:06:59 | `/tmp/ph49c_verify_run4.sql` |

`perf/sql/verify_integrity.sql`의 원본은 유지하고, 기존 EC2의 `/home/ubuntu/verify_integrity.sql`에서 회차별 사본을 만든다. 서버 파일이 저장소의 최신 SQL과 같은지 확인한다. 특히 `broken_ledger_pairs`, `balance_mismatches`, `limit_usage_mismatches`, `total_transfers`가 들어 있어야 한다.

다음은 **EC2 SSM 셸**에서 실행하는 예시다. `RUN`, `FROM_AT`, `TO_AT`은 해당 회차의 실제 `times.log` 시각으로 바꾼다. 아래 시각은 2026-10-26 **1회차 예시**이고 재측정에 그대로 쓰지 않는다.

```bash
RUN=1
FROM_AT='2026-10-26 13:26:24'
TO_AT='2026-10-26 13:50:49'
sudo -n ls -l /home/ubuntu/verify_integrity.sql
sudo -n sed \
  -e "s/^SET @from_at =.*/SET @from_at = '$FROM_AT';/" \
  -e "s/^SET @to_at.*/SET @to_at = '$TO_AT';/" \
  /home/ubuntu/verify_integrity.sql > "/tmp/ph49c_verify_run${RUN}.sql"
grep '^SET @' "/tmp/ph49c_verify_run${RUN}.sql"
grep -nE 'broken_ledger_pairs|balance_mismatches|limit_usage_mismatches|total_transfers' \
  "/tmp/ph49c_verify_run${RUN}.sql"
```

P4가 허용한 **기존 RDS 읽기 접속**으로 MySQL CLI에 접속한다. 접속 비밀번호나 명령 전체를 결과 문서에 남기지 않는다. MySQL 프롬프트에서 시각대가 `Asia/Seoul`이고 `NOW()`가 KST인지 확인한 다음 실행한다.

```sql
USE minicore;
SELECT @@session.time_zone, NOW(), UTC_TIMESTAMP();
SOURCE /tmp/ph49c_verify_run1.sql
```

2~4회차에도 위 EC2 SSM 명령의 `RUN`, `FROM_AT`, `TO_AT`을 해당 행 값으로 바꿔 사본을 만들고 `grep '^SET @'`으로 경계를 확인한다. MySQL CLI에서 각 회차 종료 후 실행한 명령은 차례로 `SOURCE /tmp/ph49c_verify_run1.sql`, `SOURCE /tmp/ph49c_verify_run2.sql`, `SOURCE /tmp/ph49c_verify_run3.sql`, `SOURCE /tmp/ph49c_verify_run4.sql`이다. 한 번에 네 파일을 실행한 것이 아니라 **각 회차가 끝난 직후 해당 파일 하나**를 실행한다.

결과표에서 원장 짝·잔액·한도 불일치가 **각각 0**인지 기록하고, `total_transfers`, `success_count`, `error_count`, `success_amount`를 별도로 적는다. `Query OK`만으로 판정하지 않는다. `limit_usage_mismatches`는 해당 구간이 속한 **날짜 전체의 한도 누계**를 검사한다. DB의 `ERROR` 건수는 HTTP 실패 건수와 같지 않다. 정합성 0도 해당 요청률이 견딤이라는 뜻은 아니다.

## 6. 실패 원인과 모니터링 확인

1~5/s와 10/s의 이체 성공률, HTTP 오류율, 유실, p95·p99를 나눠 기록한다. DB에 기록된 `ERROR`는 오류코드별로 읽기 전용 집계할 수 있다. 다음 시각은 1회차 예시다.

```sql
SELECT COALESCE(error_code, '(NULL)') AS error_code, COUNT(*) AS cases
FROM transfer
WHERE status = 'ERROR'
  AND transferred_at >= '2026-10-26 13:26:24'
  AND transferred_at <  '2026-10-26 13:50:49'
GROUP BY error_code
ORDER BY cases DESC;
```

`CMN9999`는 일반 내부 오류 코드이므로 코드만으로 원인을 확정하지 않는다. EC2 SSM 셸에서 **해당 요청률의 실제 시작·종료 시각**으로 컨테이너 로그를 조회해 최초 예외와 `Caused by:`를 확인한다. 다음 역시 1회차 10/s 예시다.

```bash
sudo -n docker logs \
  --since '2026-10-26T13:47:21+09:00' \
  --until '2026-10-26T13:51:00+09:00' \
  --timestamps corebank-server 2>&1 |
  grep -E '처리되지 않은 예외 발생|Caused by:|Exception|HikariPool|Connection is not available|Too many connections|Lock wait timeout|Deadlock' |
  head -80
```

`head -80`은 **발췌**다. 전체 구간의 오류 건수나 DB의 `ERROR` 개별 원인을 증명하지 않는다. 2026-10-26 C 측정의 10/s 서버 로그에서는 Hikari 메인 풀 `total=10, active=10, idle=0` 및 3초 커넥션 획득 타임아웃이 반복됐지만, 커넥션 점유가 길어진 상위 원인은 이 로그만으로 확정되지 않았다. 원문 로그에 고객 정보·SQL·토큰이 있으면 공유 전에 가린다.

RDS와 EC2 CloudWatch 조회 명령 및 집계 한계는 [C 결과의 RDS 명령](ph49c_jdbc_session_results.md#rds-요청률별-모니터링-조회-명령어), [EC2 명령](ph49c_jdbc_session_results.md#ec2-전체-회차-모니터링-조회-명령어)을 따른다. **RDS는 요청률별 60초 표본**, **EC2는 실제 표본 간격이 약 5분이라 회차 전체**로 정리했다. RDS `DatabaseConnections`는 RDS 전체 연결 수이지 Hikari 활성·대기 연결 수가 아니다. 잠금 대기와 세션 테이블 부하는 별도 지표 없이는 판정하지 않는다.

## 7. 결과 정리와 판정

회차마다 `times.log`, 수준별 `*_summary.json`, `*_samples.json`, `*.log`, 정합성 SQL 결과와 서버 로그·RDS·EC2 관측 범위를 비공개로 보존한다. [C 결과 문서](ph49c_jdbc_session_results.md)에는 원본 전체 대신 회차별 실제 시각·지표·예외 유형과 한계를 기록한다. 예열과 20초 점검은 네 회차 통계에서 제외한다.

이체 성공률은 **이체 API에 도달한 호출**을 분모로 한다. 앞선 계좌 비밀번호·OTP 단계에서 실패한 반복을 놓치지 않도록 HTTP 오류율과 이체 API 도달률도 함께 본다. 성공 이체 TPS는 `성공 이체 건수 ÷ 180초`의 근삿값이며 목표 `RATE`나 k6의 `iterations.rate`와 구분한다. 네 회차 중앙값과 범위를 수준별로 제시하고, [PH-30 판정 기준](harness_results.md#2-측정-결과--2026-10-05-수준별)에 따라 견딤·포화를 판정한다. B′와는 같은 지표·요청률을 비교하되 릴리스 2의 다른 코드 변경, 데이터 규모, 부하 생성 환경 차이를 PH-49c 단독 효과로 해석하지 않는다.
