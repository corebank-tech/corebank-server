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
BASE_URL='https://<P5와 확인한 기존 ALB 접속 주소>/api/v1'
RESULT_DIR="../ph49c-c-results-$(date '+%Y%m%d-%H%M%S')"
mkdir -p "$RESULT_DIR"
printf '대상=%s\n결과 폴더=%s\n' "$BASE_URL" "$RESULT_DIR"
if [[ "$(date +%z)" != '+0900' ]]; then
  echo 'Git Bash 시간대가 KST(+0900)가 아닙니다. 시각대를 맞춘 뒤 다시 시작하세요.' >&2
  return 1 2>/dev/null || exit 1
fi
{
  k6 version
  git rev-parse HEAD
  git log -1 --format=%H -- perf/k6/load.js
  git status --short -- perf/k6 perf/sql/verify_integrity.sql
  sha256sum perf/k6/load.js perf/k6/lib/*.js perf/sql/verify_integrity.sql
} | tee "$RESULT_DIR/source_provenance.txt"
```

`source_provenance.txt`의 checkout SHA·변경 상태·파일 해시·k6 버전을 결과에 기록한다. 수정된 소스가 있으면 B′와의 차이를 확인한 뒤 측정한다. 10/26 C의 `times.log`에는 `+0900`이 남아 있지만 당시 소스 상태와 k6 버전 출력은 보존되지 않았다. 10/29 재확인한 `load.js` 마지막 변경 커밋은 `f608e8effb499504d74bb5a9bd236a70df426b25`다. 실행자가 **10/26 k6를 처음 설치하고 이후 업데이트하지 않았음**을 확인했고, 10/29의 `k6 version` 출력은 `k6.exe v2.2.0 (commit/00a9a1b7f5, go1.26.5, windows/amd64)`이므로 이를 C 측정 버전으로 기록한다. 버전 출력은 사후 확인이며, 당시 로컬 checkout의 HEAD·변경 상태는 확인되지 않았다.

10/26 C 측정에는 `https://api.corebank.cloud/api/v1`을 사용했다. **10/28 컷오버 뒤 S4에는 이 주소를 그대로 사용하지 않는다.** 새 운영 환경으로 향할 수 있으므로 P5와 기존 ALB로 접속할 주소 및 HTTPS 인증서 이름에 맞는 접속 방법을 먼저 정하고, 그 주소를 위 `BASE_URL`에 넣는다. 기존 ALB의 기본 DNS 이름으로 직접 HTTPS 접속하면 인증서 이름이 맞지 않을 수 있다. 인증서 검증을 끄거나 새 운영 주소에 부하를 보내는 방식으로 우회하지 않는다. 접속 주소가 확정되지 않으면 연결 점검부터 시작하지 않는다. 위 `RESULT_DIR`은 저장소 밖에 만든다. 같은 Git Bash에서 변수·함수를 이어서 사용한다. 새 터미널을 열었다면 변수와 함수를 다시 정의한다. 결과 폴더에는 세션 정보·오류 응답이 들어갈 수 있으므로 원본 JSON·로그를 Git이나 공개 채널에 올리지 않는다.

짧은 연결 점검은 본측정과 분리해 저장한다. `setup()`에서 200개 고객 세션을 만들므로 20초 요청 구간보다 전체 실행 시간이 길 수 있다.

```bash
printf '%s CHECK START\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" | tee -a "$RESULT_DIR/times.log"
k6 run -e BASE_URL="$BASE_URL" -e RATE=1 -e DURATION=20s \
  -e MAX_VUS=200 -e SESSION_POOL=200 \
  --summary-trend-stats='avg,min,med,p(90),p(95),p(99),max' \
  --summary-export="$RESULT_DIR/check_summary.json" \
  perf/k6/load.js 2>&1 | tee "$RESULT_DIR/check.log"
status=${PIPESTATUS[0]}
printf '%s CHECK END exit=%s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$status" | tee -a "$RESULT_DIR/times.log"
printf 'check exit=%s\n' "$status"
```

`transfer_duration`, `transfer_success`, `iterations`가 출력됐는지, 인증 오류와 HTTP 실패가 없는지 확인한다. **k6 종료 코드 0만으로 개별 요청 성공을 뜻하지 않는다.** 점검 결과는 C의 4회 통계에 넣지 않는다.

**S4에서는 예열·본측정 전에**, 점검 직후 기존 RDS의 MySQL CLI에서 아래 쿼리를 실행한다. `<점검 시작 시각 KST>`에는 `times.log`의 `CHECK START` 시각을 `YYYY-MM-DD HH:MM:SS` 형식으로 넣는다. MySQL 세션 시각대가 `Asia/Seoul`인지 먼저 확인한다.

```sql
SELECT @@session.time_zone, NOW();
SELECT COUNT(*) AS new_sessions
FROM SPRING_SESSION
WHERE CREATION_TIME >= UNIX_TIMESTAMP('<점검 시작 시각 KST>') * 1000;
```

`setup()`의 로그인 세션 약 200개가 기존 RDS에 새로 생겼는지 **즉시** 확인한다. 세션이 만료·정리되기 전에 조회해야 하며, 다른 로그인도 건수에 섞일 수 있으므로 이 값만으로 경로를 단정하지 않는다. 점검 로그의 세션 생성 결과와 기존 ALB 대상 그룹·EC2의 `DB_HOST` 대조 결과를 함께 확인한다. 새 세션이 확인되지 않거나 경로가 불명확하면 예열·본측정을 중단하고 `BASE_URL`을 다시 확인한다.

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
  if [[ "$(date +%z)" != '+0900' ]]; then
    echo 'Git Bash 시간대가 KST(+0900)가 아니므로 측정을 중단합니다.' >&2
    return 1
  fi
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

`times.log`의 START·END는 `setup()` 및 종료 대기를 포함한 **k6 프로세스 경계**다. 3분 고정 도착률 시나리오의 정확한 경계와 같지 않다. 10/26 보존된 각 수준 로그에는 `duration: 3m0s`, 기본 `gracefulStop: 30s`가 표시된다. 재측정에도 실행 로그와 원시 샘플의 시각을 함께 보존하고, 3분 도착률 구간의 결과와 프로세스 전체 시간 범위를 구분한다.

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

본측정 **각 회차의 다섯 수준을 모두 마친 뒤** Git Bash의 `times.log`에서 확인한 첫 START부터 마지막 END까지를 넓은 정합성 검사 구간으로 쓴다. 이 구간은 `setup()`과 종료 대기에 완료된 이체를 포함할 수 있어 k6의 180초 도착률 구간과 같은 모집단이 아니다. k6 성공 건수와 SQL `success_count`의 차이는 경계 시각·다른 이체·응답 실패 여부를 추가 대조한다. 요청률별로 다섯 번 검사하는 절차가 아니다. 아래는 2026-10-26에 기록된 실제 시각이다. 재측정 시에는 새 `times.log` 값을 쓴다.

| 회차 | `FROM_AT` (KST) | `TO_AT` (KST) | MySQL CLI에서 실행한 파일 |
|---|---|---|---|
| 1 | 2026-10-26 13:26:24 | 2026-10-26 13:50:49 | `/tmp/ph49c_verify_run1.sql` |
| 2 | 2026-10-26 14:49:43 | 2026-10-26 15:06:49 | `/tmp/ph49c_verify_run2.sql` |
| 3 | 2026-10-26 16:09:22 | 2026-10-26 16:26:29 | `/tmp/ph49c_verify_run3.sql` |
| 4 | 2026-10-26 16:49:26 | 2026-10-26 17:06:59 | `/tmp/ph49c_verify_run4.sql` |

`perf/sql/verify_integrity.sql`의 원본은 유지하고, 기존 EC2의 `/home/ubuntu/verify_integrity.sql`에서 회차별 사본을 만든다. **측정 시점 소스 커밋과 두 파일의 SHA-256**을 기록하고 해시가 일치하는지 비교한다. 별칭 검색만으로 SQL 내용의 동일성을 판정하지 않는다. 10/26 이전 저장소의 마지막 SQL 변경 커밋은 `e21f5b3752d42f865038f26dc0af86124d0c965c`였으나, 당시 EC2 파일 해시는 보존되지 않아 과거의 파일 동일성을 소급해 확정할 수 없다.

Git Bash 저장소 루트에서 `git rev-parse HEAD`, `git log -1 --format=%H -- perf/sql/verify_integrity.sql`, `sha256sum perf/sql/verify_integrity.sql`을 실행한다. EC2 SSM 셸에서는 아래 해시를 구해 **64자리 SHA-256**을 비교한다. 다르면 사용해야 할 버전을 확인한 뒤 검사한다.

```bash
sudo -n sha256sum /home/ubuntu/verify_integrity.sql
```

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

아래에 10/s Hikari 로그 집계 및 RDS·EC2 CloudWatch 조회 명령을 둔다. **RDS는 요청률별 60초 표본**, **EC2는 실제 표본 간격이 약 5분이라 회차 전체**로 정리했다. RDS `DatabaseConnections`는 RDS 전체 연결 수이지 Hikari 활성·대기 연결 수가 아니다. 잠금 대기와 세션 테이블 부하는 별도 지표 없이는 판정하지 않는다.

### 6-1. 10/s Hikari 로그 집계

[C 결과의 10/s Hikari 로그 표](ph49c_jdbc_session_results.md#10s-hikari-커넥션-획득-타임아웃-로그-수)에서 전체 문구·Hikari `message`·`Caused by:` 포함 줄을 센 명령이다. **기존 앱 EC2의 Ubuntu 셸**에서 실행한다.

```bash
(
  set -euo pipefail
for window in \
  '1 2026-10-26T13:47:21+09:00 2026-10-26T13:50:49+09:00' \
  '2 2026-10-26T15:03:22+09:00 2026-10-26T15:06:49+09:00' \
  '3 2026-10-26T16:23:01+09:00 2026-10-26T16:26:29+09:00' \
  '4 2026-10-26T17:03:25+09:00 2026-10-26T17:06:59+09:00'
do
  read -r run start_at end_at <<< "$window"
  set +e
  sudo -n docker logs --since "$start_at" --until "$end_at" corebank-server 2>&1 |
    awk -v run="$run" '
      /Connection is not available/ { all++ }
      /"message":"HikariPool-1 - Connection is not available/ { hikari_message++ }
      /Caused by:.*Connection is not available/ { caused_by++ }
      END {
        printf "%s회차 10/s | 전체 문구 줄=%d | Hikari message 줄=%d | Caused by 포함 줄=%d\n",
          run, all, hikari_message, caused_by
      }
    '
  status=${PIPESTATUS[0]}
  set -e
  if (( status != 0 )); then echo "${run}회차 로그 조회 실패: exit=$status" >&2; break; fi
done
)
```

같은 결과 절의 고유 `correlationId` 수와 ID 없는 메시지 줄을 센 명령이다. 같은 EC2 Ubuntu 셸에서 동일한 시간 구간을 조회한다.

```bash
(
  set -euo pipefail
for window in \
  '1 2026-10-26T13:47:21+09:00 2026-10-26T13:50:49+09:00' \
  '2 2026-10-26T15:03:22+09:00 2026-10-26T15:06:49+09:00' \
  '3 2026-10-26T16:23:01+09:00 2026-10-26T16:26:29+09:00' \
  '4 2026-10-26T17:03:25+09:00 2026-10-26T17:06:59+09:00'
do
  read -r run start_at end_at <<< "$window"
  set +e
  sudo -n docker logs --since "$start_at" --until "$end_at" corebank-server 2>&1 |
    awk -v run="$run" '
      /"message":"HikariPool-1 - Connection is not available/ {
        messages++
        if (match($0, /"correlationId":"[^"]*"/)) {
          id = substr($0, RSTART + 17, RLENGTH - 18)
          if (id != "") seen[id] = 1
          else missing++
        } else missing++
      }
      END {
        for (id in seen) unique++
        printf "%s회차 10/s | Hikari 메시지=%d | 고유 correlationId=%d | ID 없음=%d\n",
          run, messages, unique, missing
      }
    '
  status=${PIPESTATUS[0]}
  set -e
  if (( status != 0 )); then echo "${run}회차 로그 조회 실패: exit=$status" >&2; break; fi
done
)
```

### 6-2. RDS 요청률별 모니터링 조회 명령어

AWS CloudShell에서 실행한다. 아래 `WINDOWS`는 2026-10-26 C 측정의 실제 구간이며, S4에서는 새 `times.log`에 맞춰 모두 교체한다. 입력 시각은 UTC, 출력의 최대값 시각은 KST다. CloudWatch의 60초 표본은 구간 경계에서 일부 겹칠 수 있다.

```bash
DB_ID='corebank-database'
(
  set -euo pipefail

while read -r run rate start_at end_at; do
  echo
  echo "### ${run}회차 ${rate}/s RDS 지표"
  echo '| 지표 | 표본 수 | 반환 표본 범위 | 구간 평균 | 최대값 (KST) |'
  echo '|---|---:|---:|---:|---:|'

  for metric in CPUUtilization DatabaseConnections ReadIOPS WriteIOPS; do
    aws cloudwatch get-metric-statistics \
      --namespace AWS/RDS \
      --metric-name "$metric" \
      --dimensions Name=DBInstanceIdentifier,Value="$DB_ID" \
      --start-time "$start_at" \
      --end-time "$end_at" \
      --period 60 \
      --statistics Average Maximum \
      --output json |
    jq -r --arg metric "$metric" '
      .Datapoints as $d |
      if ($d | length) == 0 then
        [$metric, "0", "표본 없음", "—", "—", "—"] | @tsv
      else
        ([$d[].Average] | min) as $low |
        ([$d[].Average] | max) as $high |
        ([$d[].Average] | add / length) as $mean |
        ($d | max_by(.Maximum)) as $peak |
        [
          $metric,
          ($d | length | tostring),
          "\($low * 10 | round / 10)~\($high * 10 | round / 10)",
          "\($mean * 10 | round / 10)",
          "\($peak.Maximum * 10 | round / 10)",
          $peak.Timestamp
        ] | @tsv
      end
    ' |
    while IFS=$'\t' read -r name count range mean peak timestamp; do
      if [[ "$timestamp" == "—" ]]; then
        echo "| $name | $count | $range | $mean | $peak |"
      else
        peak_kst=$(TZ=Asia/Seoul date -d "$timestamp" '+%H:%M')
        echo "| $name | $count | $range | $mean | $peak ($peak_kst) |"
      fi
    done
  done
done <<'WINDOWS'
1 1 2026-10-26T04:26:24Z 2026-10-26T04:29:48Z
1 2 2026-10-26T04:32:09Z 2026-10-26T04:35:32Z
1 3 2026-10-26T04:40:01Z 2026-10-26T04:43:25Z
1 5 2026-10-26T04:43:29Z 2026-10-26T04:46:52Z
1 10 2026-10-26T04:47:21Z 2026-10-26T04:50:49Z
2 1 2026-10-26T05:49:43Z 2026-10-26T05:53:08Z
2 2 2026-10-26T05:53:08Z 2026-10-26T05:56:32Z
2 3 2026-10-26T05:56:32Z 2026-10-26T05:59:54Z
2 5 2026-10-26T05:59:54Z 2026-10-26T06:03:22Z
2 10 2026-10-26T06:03:22Z 2026-10-26T06:06:49Z
3 1 2026-10-26T07:09:22Z 2026-10-26T07:12:48Z
3 2 2026-10-26T07:12:48Z 2026-10-26T07:16:13Z
3 3 2026-10-26T07:16:13Z 2026-10-26T07:19:36Z
3 5 2026-10-26T07:19:36Z 2026-10-26T07:23:01Z
3 10 2026-10-26T07:23:01Z 2026-10-26T07:26:29Z
4 1 2026-10-26T07:49:26Z 2026-10-26T07:52:56Z
4 2 2026-10-26T07:52:57Z 2026-10-26T07:56:25Z
4 3 2026-10-26T07:56:25Z 2026-10-26T07:59:52Z
4 5 2026-10-26T07:59:52Z 2026-10-26T08:03:25Z
4 10 2026-10-26T08:03:25Z 2026-10-26T08:06:59Z
WINDOWS
)
```

### 6-3. EC2 전체 회차 모니터링 조회 명령어

AWS CloudShell에서 실행한다. 아래 구간과 인스턴스 ID는 2026-10-26 C 측정 값이므로 S4에서는 새 대상·`times.log` 시각으로 교체한다. 조회 시각은 UTC, 출력의 최대값 시각은 KST다. CPU·네트워크는 `--period 60`, CPU 크레딧은 `--period 300`으로 요청했다. C에서 실제 CPU·네트워크 반환 시각은 약 5분 간격이었다.

```bash
INSTANCE_ID='i-0c861fe2dc634f3af'
(
  set -euo pipefail

for window in \
  '1 2026-10-26T04:26:00Z 2026-10-26T04:51:00Z' \
  '2 2026-10-26T05:49:00Z 2026-10-26T06:07:00Z' \
  '3 2026-10-26T07:09:00Z 2026-10-26T07:27:00Z' \
  '4 2026-10-26T07:49:00Z 2026-10-26T08:07:00Z'
do
  read -r run start_at end_at <<< "$window"

  echo
  echo "### ${run}회차 EC2 지표"
  echo '| 지표 | 반환 표본 범위 | 구간 평균 | 최대값 (KST) |'
  echo '|---|---:|---:|---:|'

  for metric in CPUUtilization NetworkIn NetworkOut NetworkPacketsIn NetworkPacketsOut CPUCreditUsage CPUCreditBalance; do
    case "$metric" in
      CPUCreditUsage|CPUCreditBalance) period=300 ;;
      *) period=60 ;;
    esac

    aws cloudwatch get-metric-statistics \
      --namespace AWS/EC2 \
      --metric-name "$metric" \
      --dimensions Name=InstanceId,Value="$INSTANCE_ID" \
      --start-time "$start_at" \
      --end-time "$end_at" \
      --period "$period" \
      --statistics Average Maximum \
      --output json |
    jq -r --arg metric "$metric" --arg period "$period" '
      .Datapoints as $d |
      if ($d | length) == 0 then
        [$metric + " (" + $period + "초)", "표본 없음", "—", "—", "—"] | @tsv
      else
        ([$d[].Average] | min) as $low |
        ([$d[].Average] | max) as $high |
        ([$d[].Average] | add / length) as $mean |
        ($d | max_by(.Maximum)) as $peak |
        [
          $metric + " (" + $period + "초)",
          "\($low * 10 | round / 10)~\($high * 10 | round / 10)",
          "\($mean * 10 | round / 10)",
          "\($peak.Maximum * 10 | round / 10)",
          $peak.Timestamp
        ] | @tsv
      end
    ' |
    while IFS=$'\t' read -r name range mean peak timestamp; do
      if [[ "$timestamp" == "—" ]]; then
        echo "| $name | $range | $mean | $peak |"
      else
        peak_kst=$(TZ=Asia/Seoul date -d "$timestamp" '+%H:%M')
        echo "| $name | $range | $mean | $peak ($peak_kst) |"
      fi
    done
  done
done
)
```

## 7. 결과 정리와 판정

회차마다 `times.log`, 수준별 `*_summary.json`, `*_samples.json`, `*.log`, 정합성 SQL 결과와 서버 로그·RDS·EC2 관측 범위를 비공개로 보존한다. [C 결과 문서](ph49c_jdbc_session_results.md)에는 원본 전체 대신 회차별 실제 시각·지표·예외 유형과 한계를 기록한다. 예열과 20초 점검은 네 회차 통계에서 제외한다.

이체 성공률은 **이체 API에 도달한 호출**을 분모로 한다. 앞선 계좌 비밀번호·OTP 단계에서 실패한 반복을 놓치지 않도록 HTTP 오류율과 이체 API 도달률도 함께 본다. 성공 이체 TPS는 `성공 이체 건수 ÷ 180초`의 근삿값이며 목표 `RATE`나 k6의 `iterations.rate`와 구분한다. 네 회차 중앙값과 범위를 수준별로 제시하고, [PH-30 판정 기준](harness_results.md#2-측정-결과--2026-10-05-수준별)에 따라 견딤·포화를 판정한다. B′와는 같은 지표·요청률을 비교하되 릴리스 2의 다른 코드 변경, 데이터 규모, 부하 생성 환경 차이를 PH-49c 단독 효과로 해석하지 않는다.

### #790 기존 서버 로그 보존·PC 다운로드

10/26 C 측정의 1·2회차 10/s 전체 로그는 기존 EC2에서 각각 `/tmp/ph49c_run1_full.log`, `/tmp/ph49c_run2_full.log`로 조회했다. 컨테이너 재생성이나 EC2 종료 전에 원본을 비공개로 보존한다. 로그에는 고객 정보·토큰 등이 포함될 수 있으므로 Git, 공개 이슈, 채팅에 원문을 올리지 않는다. 조사 결과에는 집계값과 필요한 부분을 가린 발췌만 남긴다. **원본 파일을 이 가이드 작성자에게 전달할 필요는 없다.**

기존 EC2의 Ubuntu SSM 셸에서 `/tmp` 밖에 권한을 제한한 사본과 해시를 만든다. 이미 사본과 해시가 있다면 덮어쓰지 않고 검증한다.

```bash
umask 077
EVIDENCE_DIR="$HOME/ph49c-790-evidence"
mkdir -p "$EVIDENCE_DIR"
for run in 1 2; do
  if [[ ! -e "$EVIDENCE_DIR/ph49c_run${run}_full.log" ]]; then
    cp "/tmp/ph49c_run${run}_full.log" "$EVIDENCE_DIR/"
  fi
done
chmod 600 "$EVIDENCE_DIR"/*.log
cd "$EVIDENCE_DIR"
if [[ ! -e SHA256SUMS ]]; then
  sha256sum ph49c_run1_full.log ph49c_run2_full.log > SHA256SUMS
fi
sha256sum -c SHA256SUMS
tar -czf ph49c-790-evidence.tar.gz ph49c_run1_full.log ph49c_run2_full.log SHA256SUMS
chmod 600 ph49c-790-evidence.tar.gz
sha256sum ph49c-790-evidence.tar.gz
```

PC에서 **기존에 허용된 SSH 접속 경로**로 내려받는다. 아래는 Windows Git Bash 명령이다. `KEY`와 `EC2_HOST`는 평소 이 EC2에 SSH 접속할 때 사용한 개인 키 경로와 호스트로 바꾼다. SSH가 직접 닿지 않는 환경이라면 보안 그룹을 새로 열지 말고 기존 SSM/SSH 접속 방법에 맞춰 파일 전송 방법을 확인한다. EC2에 AWS CLI를 설치하거나 Terraform 상태용 S3 버킷을 사용하지 않는다.

```bash
KEY="$HOME/.ssh/<실제-키파일>.pem"
EC2_HOST='<기존-SSH-접속-주소>'
LOCAL_EVIDENCE="$HOME/Intellij-workspace/ph49c-790-evidence"
mkdir -p "$LOCAL_EVIDENCE"
scp -i "$KEY" -o IdentitiesOnly=yes \
  "ubuntu@$EC2_HOST:/home/ubuntu/ph49c-790-evidence/ph49c-790-evidence.tar.gz" \
  "$LOCAL_EVIDENCE/"
cd "$LOCAL_EVIDENCE"
tar -xzf ph49c-790-evidence.tar.gz
sha256sum -c SHA256SUMS
```

두 로그 모두 `OK`가 나와야 한다. 이 로컬 폴더는 `corebank-server` 저장소 밖에 둔다. EC2 홈의 사본도 EC2가 종료되면 사라질 수 있으므로 종료 전에 다운로드와 해시 검증을 마친다.
