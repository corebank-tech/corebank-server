# PH-28 시산표 원시집계 베이스라인 측정 결과

> 관리자 시산표 API(`GET /admin/trial-balance`)가 쓰는 계정별 집계 SQL의 개선 전 성능을 기록한다.
> GL-IDX에서 인덱스를 붙인 뒤 동일 EC2·동일 데이터·동일 기간과 checksum을 기준으로 비교한다.
> 재측정 시 기존 결과를 지우지 않고 추가한다.

## 0. 측정 시점과 대상

- 측정은 Redis 제거 판정 측정(#593, PH-101-②)이 끝난 뒤에 한다. 그 전에는 운영에서 재지 않는다([README](README.md) 10/16 이후 행).
  60b 베이스라인(PH-11 · PH-28 · PH-38 · PH-43)과 시각이 겹치지 않게 한 번에 하나씩 잰다.
- 데이터: PH-60b 운영 적재분(분개 6,470,002행, 거래일 2026-09-01 ~ 2026-10-05, [ph60b_bulk_seed.md](ph60b_bulk_seed.md)). 측정일 테이블 전체는 6,547,904행이다. 10/6 이후 운영 거래가 약 7.8만 행 더했고, 측정 기간 밖이다.
- 쿼리: [`perf/sql/ph28_trial_balance_baseline.sql`](../../perf/sql/ph28_trial_balance_baseline.sql). `GlJournalEntryJpaRepository.sumByAccount`와 같은 문장이다.
- 인덱스: `gl_journal_entry`에 PK · `uk_gl_journal_entry_line` · FK 인덱스만 있는 상태(GL-IDX 전).

## 1. 측정 방법

기간마다 웜업 1회 뒤 `EXPLAIN ANALYZE`를 20회 실행하고, 최상위 노드 `actual time`의 마지막 값(ms)을 1회 실행 시간으로 쓴다.
서버 실행 시간만 재므로 네트워크·드라이버 시간은 들어가지 않는다. 20회 표본이라 p95는 정렬 19번째, p99는 최댓값이다.
**비교 기준은 웜업 뒤 20회의 평균·p95·p99뿐이다.** 웜업 회차 값은 "첫 회차"로 참고만 한다 — 기간을 연달아 재므로
두 번째 기간부터는 앞 측정이 버퍼 풀을 데운 상태라 콜드 수치가 아니다.

```bash
# EC2에서 실행. 접속 정보는 환경변수로 넘긴다(MYSQL_HOST, MYSQL_USER, MYSQL_PWD, MYSQL_DB).
measure() {
  local from=$1 to=$2
  local sql="SET @from_date='$from'; SET @to_date='$to'; $(sed -n '/^EXPLAIN ANALYZE/,/^ORDER BY s.account_code;/p' perf/sql/ph28_trial_balance_baseline.sql)"
  local i out timing
  for i in $(seq 0 20); do
    # 실패하거나 시간을 못 읽은 회차를 표본에 넣지 않는다. 그 자리에서 멈추고 기간을 다시 잰다.
    if ! out=$(mysql -h "$MYSQL_HOST" -u "$MYSQL_USER" "$MYSQL_DB" -N -r -e "$sql"); then
      echo "mysql failed at iteration $i" >&2
      return 1
    fi
    timing=$(printf '%s\n' "$out" | sed -nE '1s/.*actual time=[0-9.]+\.\.([0-9.]+).*/\1/p')
    if [[ ! "$timing" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
      echo "invalid actual time at iteration $i: $timing" >&2
      return 1
    fi
    echo "$([[ $i -eq 0 ]] && echo first || echo warm) $timing"
  done
}
measure 2026-10-01 2026-10-01 && measure 2026-09-01 2026-09-30 && measure 2026-09-01 2026-10-05
```

같은 SQL 파일의 0번(데이터 확인)과 `SHOW INDEX` 결과도 함께 남긴다.

### 실행기

위 `measure()`는 [`perf/sql/ph28/run_baseline.sh`](../../perf/sql/ph28/run_baseline.sh)에 그대로 들어 있다. 실행기는 여기에 아래를 더한다.

- 회차별 시작과 종료 시각(초), 회차마다 `EXPLAIN ANALYZE` 전문 저장
- 측정 앞뒤 테이블 전체 행 수. 인덱스가 없으면 기간과 무관하게 테이블 전체를 읽어서, 시간이 기간 행 수가 아니라 전체 행 수에 달려 있다
- **교차 측정**: 전 구간을 계측 없는 일반 SELECT로 3회 재고(클라이언트 벽시계), 접속 시간(`SELECT 1`)을 따로 잰다. `EXPLAIN ANALYZE`의 행별 계측 비용을 가늠하기 위해서다. 결과 행은 tsv로 남겨 개선 후 결과와 비교한다
- `summary`: 문서 규칙대로 평균, p95(정렬 19번째), p99(최댓값), 앞 10회와 뒤 10회 평균을 계산한다. 앞뒤 평균이 벌어지면 측정 중 환경이 바뀐 것이다

```bash
# 기존 EC2. 운영 compose 파일에서 DB 접속 정보를 읽는다. tmux 안에서 실행한다
./run_baseline.sh check   # 실행 중인 app_user 쿼리 확인(시작 전, 중단 뒤)
./run_baseline.sh dry     # EXPLAIN(실행 안 함)으로 SQL 추출과 실행계획 확인
./run_baseline.sh run
./run_baseline.sh summary logs/ph28_run_<시각>.log
```

### API 응답 시간

[`perf/sql/ph28/run_api.sh`](../../perf/sql/ph28/run_api.sh)가 EC2 안에서 운영 컨테이너(`localhost:80`)로 `GET /api/v1/admin/trial-balance`를 부른다. 인터넷 구간을 빼기 위해서다.
기간마다 관리자 로그인 뒤 예열 1회와 5회를 잰다(curl `time_total`). 응답 JSON을 회차마다 남겨 개선 후 결과와 비교한다. 아이디와 비밀번호는 실행 때 화면에서 입력한다.

**측정 SQL과 앱 쿼리의 차이.** 측정 SQL은 기간을 문자열 사용자 변수(`SET @from_date = '2026-09-01'`)로 넣고, 앱은 날짜 값으로 넣는다. 실행계획은 같지만 문자열은 행마다 비교 비용이 더 든다(로컬 60b, 하루 기간 약 6~10%). 그래서 DB 측정 값이 API 값보다 크게 나올 수 있다. 개선 전후는 같은 방식끼리만 비교한다.

## 2. 개선 전 베이스라인

### 측정 조건

| 항목 | 값 |
| --- | --- |
| 측정 일시 | DB 2026-10-28 09:40:54 ~ 10:09:09 KST, API 10:54:37 ~ 11:01:23 KST |
| 측정 환경 | 기존 단일 EC2와 기존 RDS(db.t4g.micro, MySQL 8.4.10, 버퍼 풀 128MB, gp3). 컷오버 전이라 운영 트래픽이 조금 있었다(측정 직전 1시간 RDS CPU 약 4%). 스레드 공지 기준으로 같은 시간대 다른 측정은 없었다 |
| Docker 이미지 | `176fe574`(릴리스 2) |
| 측정 SQL sha256 | `cb643b4b`로 시작(`perf/sql/ph28_trial_balance_baseline.sql`) |
| 테이블 전체 행 수 | 6,547,904(측정 전후 같음) |
| `journal_entry_rows` (전 구간) | 6,470,002 |
| `debit_checksum` = `credit_checksum` (전 구간) | 1,754,632,400,000 = 1,754,632,400,000 |
| 인덱스 | PK, `uk_gl_journal_entry_line`, `fk_gl_journal_entry_voucher`, `fk_gl_journal_entry_account`(GL-IDX 전) |

09:30:36에 시작한 첫 run은 하루 기간 20회 뒤 실행기 버그로 멈췄다. 고친 실행기로 처음부터 다시 쟀고, 아래는 두 번째 run이다. 첫 run의 하루 기간은 평균 21,696.5ms, p95 22,511ms로 두 번째 run과 4.5% 안쪽이다.

### 측정 결과 (`EXPLAIN ANALYZE`, 기간마다 예열 1 + 20회)

| 기간 | 대상 분개 행 | 평균(ms) | p95(ms) | p99(ms) | 첫 회차(ms, 참고) | 앞 10회 / 뒤 10회 평균(ms) | 결과 행 | 차대 일치 |
| --- | ---: | ---: | ---: | ---: | ---: | --- | ---: | --- |
| 하루 10/1 | 585,632 | 22,674.5 | 23,286 | 24,752 | 22,844 | 22,580 / 22,769 | 1 | 일치 |
| 한 달 9/1~9/30 | 4,470,002 | 25,322.3 | 25,860 | 26,140 | 25,838 | 25,260 / 25,385 | 2 | 일치 |
| 전 구간 9/1~10/5 | 6,470,002 | 26,820.0 | 27,376 | 29,984 | 26,432 | 26,954 / 26,686 | 2 | 일치 |

- 세 기간 모두 655만 행을 전부 읽는다. 기간이 길수록 느린 것은 집계하는 행 수 차이다. 로컬 재현(§3)에서도 같은 순서로 길어졌고, 로컬에는 디스크 버스트 제한이 없다.
- 앞 10회와 뒤 10회 평균이 1% 안쪽이라 측정 중 디스크 버스트 소진 같은 저하는 없었다.
- 20개 표본의 p99는 최댓값이라 비교에는 평균과 p95를 쓴다.
- **정각 작업과 겹친 회차.** 세 기간의 최댓값 중 둘이 매시 정각 작업 시각을 지났다. 표본에서 빼지 않고 표시만 한다. 두 회차 모두 최댓값이라 p95에는 들지 않는다.

| 기간 | 회차 | 시간(ms) | 지난 시각 | 그 회차를 뺀 평균(ms) |
| --- | --- | ---: | --- | ---: |
| 하루 10/1 | 본측정 10회 | 24,752 | 09:45 | 22,565.1 (n=19) |
| 전 구간 9/1~10/5 | 본측정 4회 | 29,984 | 10:00 | 26,653.4 (n=19) |

  10:00에는 RDS `DiskQueueDepth`(최대)가 측정 중 평소의 약 3배로 튀었고, `DataVolumeIOPSExceededCheck`가 한 번 0보다 컸다. 그 순간 디스크 IOPS 한도를 잠깐 넘은 것으로 보인다(추정, 5분 단위 그래프 판독).
- 결과 행: 10월은 당행 이체만 있어 `20100` 한 행이다(이체는 차변과 대변이 모두 `20100`, [gl_journal_patterns.md](gl_journal_patterns.md) §3-2). 9월에는 개시 분개의 `10100`이 더해진다.

### 교차 측정 (전 구간, 계측 없음)

| 항목 | 값 |
| --- | --- |
| 일반 SELECT 3회(ms) | 25,245 / 25,995 / 25,269, 평균 25,503.0 |
| 접속 시간 3회 평균(ms) | 45.7 |
| `EXPLAIN ANALYZE` 평균과의 차이 | 약 1.3초(약 5%). 행별 계측 비용이다 |

### API 응답 시간 (`GET /admin/trial-balance`, 기간마다 예열 1 + 5회)

| 기간 | 평균(ms) | 중앙값(ms) | 최댓값(ms) | 첫 회차(ms, 참고) | 응답 |
| --- | ---: | ---: | ---: | ---: | --- |
| 하루 10/1 | 19,066.8 | 19,244.4 | 19,517.6 | 24,103.0 | 18회 모두 200, `0000`, `balanced=true` |
| 한 달 9/1~9/30 | 22,655.3 | 22,569.5 | 23,131.4 | 23,316.6 | |
| 전 구간 9/1~10/5 | 24,870.1 | 24,058.5 | 27,789.6 | 24,105.4 | |

전 구간 본측정 2회(27,789.6ms)는 11:00 정각 작업 시각을 지났다. 빼면 평균 24,140.3ms(n=4)다.

API가 DB 측정보다 짧은 것은 기간 값의 형식 차이 때문으로 본다(§1 "측정 SQL과 앱 쿼리의 차이"). 로컬에서 재현한 차이(6~10%)가 운영 차이(기간별 7~16%)를 다 설명하지는 않는다. 나머지 원인은 확인하지 않았다.

### RDS 지표 (콘솔 모니터링, 5분 단위 그래프 판독)

| 지표 | 측정 직전 (08:30~09:30) | DB 측정 중 (09:30~10:09) | API 측정 중 (10:54~11:01) |
| --- | --- | --- | --- |
| `CPUUtilization` | 약 4% | 약 35~46% | 약 46%까지 |
| `EBSByteBalance%` | 99% | 최저 약 88% | 회복 중, 약 90% 이상 |
| `EBSIOBalance%` | 99% | 최저 약 86% | 회복 중 |
| `CPUCreditBalance` | 288 | 최저 약 262 | 회복 중. `CPUSurplusCreditsCharged` 0 |
| `InstanceEBSIOPSExceededCheck`, `InstanceEBSThroughputExceededCheck` | 0 | 0 | 0 |

디스크와 CPU 버스트 잔량이 끝까지 0에 가깝게 떨어지지 않았다. 그래서 측정 중 성능 저하는 없었다고 본다. 원본 캡처는 측정 담당 개인 기록에 보관했다.

### 실행계획 (전 구간, 본측정 10회차)

```text
-> Nested loop left join  (cost=342731 rows=1) (actual time=26595..26595 rows=2 loops=1)
    -> Sort: s.account_code  (cost=168290..168290 rows=1) (actual time=26590..26590 rows=2 loops=1)
        -> Table scan on s  (cost=168290..168290 rows=1) (actual time=26584..26584 rows=2 loops=1)
            -> Materialize  (cost=168287..168287 rows=1) (actual time=26583..26583 rows=2 loops=1)
                -> Group aggregate: sum((case when (je.dr_cr = 'DEBIT') then je.amount else 0 end)), sum((case when (je.dr_cr = 'CREDIT') then je.amount else 0 end)), count(0)  (cost=168287 rows=1) (actual time=8.84..26576 rows=2 loops=1)
                    -> Filter: (je.trade_date between <cache>((@from_date)) and <cache>((@to_date)))  (cost=98511 rows=697765) (actual time=8.83..23290 rows=6.47e+6 loops=1)
                        -> Index scan on je using fk_gl_journal_entry_account  (cost=98511 rows=6.28e+6) (actual time=8.81..20585 rows=6.55e+6 loops=1)
    -> Single-row index lookup on a using PRIMARY (account_code=s.account_code)  (cost=0.25 rows=1) (actual time=2.03..2.03 rows=1 loops=2)
```

- 옵티마이저는 `GROUP BY account_code` 순서를 얻으려고 `fk_gl_journal_entry_account`로 테이블 전체(655만 행)를 훑는다. 행마다 본 테이블을 다시 찾아가 `trade_date`, `dr_cr`, `amount`를 읽고, 기간 필터는 그 뒤에 건다.
- 앱처럼 날짜를 리터럴로 넣은 `EXPLAIN`도 같은 계획이었다.

## 3. GL-IDX 인덱스 결정

### 후보 비교 (로컬, 2026-10-28)

기존 RDS에는 인덱스가 하나만 생기므로, 후보는 PH-60b 적재 데이터로 로컬에서 비교했다. [`perf/sql/ph28/index_compare_local.sh`](../../perf/sql/ph28/index_compare_local.sh)

- 환경: Docker MySQL 8.4.10, 버퍼 풀 128MB, PH-60b 실적재 6,470,002행(PH-28b 측정용 컨테이너). 인덱스는 운영과 같다
- 방법: 후보마다 인덱스 하나만 만든 상태에서 같은 측정 SQL을 기간마다 예열 1회 + 5회. 집계 결과 행을 인덱스 없을 때와 비교했다
- 로컬 디스크가 운영보다 빨라 절대 시간은 운영보다 짧다. 후보 사이의 상대 비교로만 쓴다

| 후보 | 하루(ms) | 한 달(ms) | 전 구간(ms) | 읽는 방식 | 결과 |
| --- | ---: | ---: | ---: | --- | --- |
| 추가 인덱스 없음 | 7,982 | 8,950 | 9,524 | `fk_gl_journal_entry_account` 전체 + 본 테이블 | 기준 |
| `(account_code, trade_date)` | 19,125 | 19,701 | 20,017 | 새 인덱스 전체 + 본 테이블 | 2배 이상 느림 |
| **`(trade_date, account_code, dr_cr, amount)`** | **449** | **3,893** | **5,598** | 기간 범위만, 본 테이블 안 읽음 | 기준과 같음 |
| `(account_code, trade_date, dr_cr, amount)` | 3,099 | 4,091 | 4,584 | 새 인덱스 전체, 본 테이블 안 읽음 | 기준과 같음 |

- `(account_code, trade_date)`는 기간 범위로 좁히지 못해 인덱스 전체를 읽고, 기간에 맞는 행마다 본 테이블을 찾는다. 기존 FK 인덱스는 같은 계정 안에서 행 번호 순서라 본 테이블을 차례로 읽지만, 이 인덱스는 거래일 순서라 흩어 읽는다(추정).
- 커버링 두 후보의 생성 시간은 로컬 약 14.4초, 크기는 265MB다.
- **`account_code`로 시작하는 인덱스는 FK 인덱스를 조용히 지운다.** `fk_gl_journal_entry_account`는 FK가 자동으로 만든 인덱스라, 같은 FK를 받칠 수 있는 인덱스가 생기면 MySQL이 지운다. 그러면 새 인덱스를 되돌릴 때 오류 1553이 나고, 스키마 문서와 실제가 어긋난다. 로컬에서 `(account_code, trade_date)`를 만들 때 실제로 일어났다.

### 결정

- **인덱스: `ix_gl_journal_entry_trade_date (trade_date, account_code, dr_cr, amount)`.** 기간이 짧을수록 크게 빨라지고(로컬 하루 17.8배), 분개가 쌓여도 조회 기간만큼만 읽는다. `(account_code, trade_date, dr_cr, amount)`는 전 구간이 약 1초 빠르지만, 기간과 무관하게 인덱스 전체를 읽어 분개가 쌓일수록 모든 조회가 느려지고, FK 인덱스 문제도 있다.
- **쿼리 타임아웃: 30초**(`TrialBalanceQueryService`, `@Transactional(timeout)`). 개선 전 전 구간 p95가 27.4초, 최댓값이 30.0초라, 인덱스가 빠진 상황에서도 정상 조회는 거의 자르지 않는다. 넘기면 JDBC 쿼리 타임아웃으로 끊기고 공통 오류(`CMN9999`)로 응답한다.

### 개선 후 측정 조건

- 기존 EC2에 릴리스 3을 배포해 Flyway가 기존 RDS에 인덱스를 만든 뒤(P4, 늦어도 11/2), 11/6 전에 잰다.
- 같은 실행기, 같은 기간 3개와 순서, 같은 반복 횟수. DB와 API 둘 다 잰다.
- `dry`에서 세 기간 모두 `ix_gl_journal_entry_trade_date` 범위 스캔인지 먼저 확인한다.
- 같은 결과인지 확인한다. 기간별 checksum, 교차 측정 tsv, API 응답 JSON의 `rows`를 개선 전 파일과 비교한다.
- 조건 차이를 함께 적는다. 개선 후는 컷오버 뒤라 운영 트래픽이 없고, 테이블 전체 행 수가 다를 수 있다(기존 환경의 COB 등). API는 릴리스 3 배포본이라 인덱스 말고 다른 코드 변경도 섞인다. 그래서 개선 폭의 근거는 같은 SQL의 DB 측정으로 든다.

## 4. 참고 — Fineract와의 비교

Apache Fineract(develop `ddb1ca0`, 2026-10-13 기준)는 같은 문제를 사전 집계로 푼다.

- 리포트 SQL(`0084_add_general_accounting_table_reports.xml`)은 우리와 같이 `SUM(CASE WHEN type_enum = … THEN amount ELSE 0 END)`로
  계정별 차변·대변을 합산한다. 다만 기초잔액 · 기간 증감 · 기말잔액을 내는 합계잔액시산표라 기준일 이전 분개를 전부 읽는다.
- 그 비용 때문에 배치(`UpdateTrialBalanceDetailsTasklet`)가 일별·계정별 금액과 누적 `closing_balance`를 `m_trial_balance`에 미리 쌓는다.
- 분개 테이블 인덱스는 날짜 단일 인덱스(`transaction_date_index`, `submitted_on_date`)와 계정 FK 인덱스뿐이고 복합 인덱스는 없다.

우리는 기간 안 차변·대변 합계만 내는 합계시산표이고, 마감잔액 테이블은 2차 범위 밖이다(tasks.md GL-IDX · PH-75).
그래서 원시 집계 + 인덱스로 간다. 이 결과에서 인덱스로도 목표에 못 미치면 사전 집계가 다음 후보다.
