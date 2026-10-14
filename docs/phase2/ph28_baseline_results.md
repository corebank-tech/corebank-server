# PH-28 시산표 원시집계 베이스라인 측정 결과

> 관리자 시산표 API(`GET /admin/trial-balance`)가 쓰는 계정별 집계 SQL의 개선 전 성능을 기록한다.
> GL-IDX에서 인덱스를 붙인 뒤 동일 EC2·동일 데이터·동일 기간과 checksum을 기준으로 비교한다.
> 재측정 시 기존 결과를 지우지 않고 추가한다.

## 0. 측정 시점과 대상

- 측정은 Redis 제거 판정 측정(#593, PH-101-②)이 끝난 뒤에 한다. 그 전에는 운영에서 재지 않는다([README](README.md) 10/16 이후 행).
  60b 베이스라인(PH-11 · PH-28 · PH-38 · PH-43)과 시각이 겹치지 않게 한 번에 하나씩 잰다.
- 데이터: PH-60b 운영 적재분(분개 약 647만 행, 거래일 2026-09-01 ~ 2026-10-05, [ph60b_bulk_seed.md](ph60b_bulk_seed.md)).
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
  for i in $(seq 0 20); do
    mysql -h "$MYSQL_HOST" -u "$MYSQL_USER" "$MYSQL_DB" -N -r -e "$sql" \
      | head -1 | sed -E 's/.*actual time=[0-9.]+\.\.([0-9.]+).*/\1/' \
      | awk -v i="$i" '{ print (i == 0 ? "first" : "warm"), $1 }'
  done
}
measure 2026-10-01 2026-10-01
measure 2026-09-01 2026-09-30
measure 2026-09-01 2026-10-05
```

같은 SQL 파일의 0번(데이터 확인)과 `SHOW INDEX` 결과도 함께 남긴다.

## 2. 개선 전 베이스라인

### 측정 조건

| 항목 | 값 |
| --- | --- |
| 측정 일시 | |
| 측정 환경 | 기존 단일 EC2 · 운영 MySQL |
| Docker 이미지 / commit SHA | |
| `journal_entry_rows` (전 구간) | |
| `debit_checksum` = `credit_checksum` (전 구간) | |

### 측정 결과

| 기간 | 대상 분개 행 | 평균(ms) | p95(ms) | p99(ms) | 첫 회차(ms, 참고) | 결과 행 | 차대 일치 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 하루 10/1 | | | | | | | |
| 한 달 9/1~9/30 | | | | | | | |
| 전 구간 9/1~10/5 | | | | | | | |

### 실행계획

전 구간 `EXPLAIN ANALYZE` 출력 전문을 붙인다. 접근 방식(전체 스캔 여부), 임시 테이블, 정렬 단계를 확인한다.

## 3. GL-IDX 비교 기준

- 재측정 때 동일 EC2, 동일 데이터(0번 checksum 일치), 동일 기간 3개, 동일 측정 순서, 동일 반복 횟수를 유지한다.
- **API 쿼리 타임아웃은 이 결과를 보고 정한다.** 지금 `TrialBalanceQueryService`에는 타임아웃이 없다(레포에 선례도 없음).
  전 구간 p99를 보고 정상 조회가 잘리지 않는 값으로 `@Transactional(timeout)`을 건다. 인덱스 전에는 전체 스캔이 커넥션을 오래 잡는다.
- **인덱스 컬럼 순서는 이 실행계획을 보고 정한다.** tasks.md GL-IDX는 `(account_code, trade_date)`를 적어 뒀지만,
  이 쿼리는 기간으로 거른 뒤 계정으로 묶는다. 기간 범위 스캔을 타려면 `trade_date`가 앞에 와야 하고,
  `(trade_date, account_code, dr_cr, amount)`면 테이블을 읽지 않는 커버링 인덱스가 된다.
  반대로 계정이 15개뿐이라 `(account_code, trade_date)`가 계정별 범위 스캔으로 이길 수도 있다. 두 후보를 같은 조건에서 잰다.

## 4. 참고 — Fineract와의 비교

Apache Fineract(develop `ddb1ca0`, 2026-10-13 기준)는 같은 문제를 사전 집계로 푼다.

- 리포트 SQL(`0084_add_general_accounting_table_reports.xml`)은 우리와 같이 `SUM(CASE WHEN type_enum = … THEN amount ELSE 0 END)`로
  계정별 차변·대변을 합산한다. 다만 기초잔액 · 기간 증감 · 기말잔액을 내는 합계잔액시산표라 기준일 이전 분개를 전부 읽는다.
- 그 비용 때문에 배치(`UpdateTrialBalanceDetailsTasklet`)가 일별·계정별 금액과 누적 `closing_balance`를 `m_trial_balance`에 미리 쌓는다.
- 분개 테이블 인덱스는 날짜 단일 인덱스(`transaction_date_index`, `submitted_on_date`)와 계정 FK 인덱스뿐이고 복합 인덱스는 없다.

우리는 기간 안 차변·대변 합계만 내는 합계시산표이고, 마감잔액 테이블은 2차 범위 밖이다(tasks.md GL-IDX · PH-75).
그래서 원시 집계 + 인덱스로 간다. 이 결과에서 인덱스로도 목표에 못 미치면 사전 집계가 다음 후보다.
