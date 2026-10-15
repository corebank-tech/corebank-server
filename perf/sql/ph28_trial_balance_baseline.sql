-- PH-28 시산표 원시집계 베이스라인
--
-- 결과는 docs/phase2/ph28_baseline_results.md 에 적는다. 측정 절차(반복 횟수, 웜업)도 그 문서에 있다.
-- 1번의 집계 쿼리는 GlJournalEntryJpaRepository.SUM_BY_ACCOUNT_SQL 과 같은 문장이어야 한다(파라미터만 @변수).
-- 다르면 잰 숫자가 API 의 숫자가 아니다. TrialBalanceBaselineSqlTest 가 둘을 대조하므로 한쪽만 고치면 테스트가 깨진다.
--
-- 인덱스를 붙이기 전(GL-IDX 전)에 잰다. 붙인 뒤 같은 기간·같은 데이터로 다시 재서 비교한다.
--
-- 측정 기간 3개(PH-60b 거래일 9/1~10/5 기준):
--   하루    2026-10-01 ~ 2026-10-01
--   한 달   2026-09-01 ~ 2026-09-30
--   전 구간 2026-09-01 ~ 2026-10-05
SET @from_date = '2026-09-01';
SET @to_date   = '2026-09-30';

-- ---------------------------------------------------------------------------
-- 0. 데이터 확인 — 재측정 때 같은 데이터인지 이 값으로 대조한다. 두 checksum 이 같으면 차대 일치다
-- ---------------------------------------------------------------------------
SELECT COUNT(*)                                                 AS journal_entry_rows,
       SUM(CASE WHEN dr_cr = 'DEBIT'  THEN amount ELSE 0 END)   AS debit_checksum,
       SUM(CASE WHEN dr_cr = 'CREDIT' THEN amount ELSE 0 END)   AS credit_checksum
FROM gl_journal_entry
WHERE trade_date BETWEEN @from_date AND @to_date;

SHOW INDEX FROM gl_journal_entry;

-- ---------------------------------------------------------------------------
-- 1. 실행계획과 실행 시간 — 최상위 노드의 actual time 마지막 값(ms)이 1회 실행 시간이다
-- ---------------------------------------------------------------------------
EXPLAIN ANALYZE
SELECT s.account_code    AS accountCode,
       a.account_name    AS accountName,
       a.account_class   AS accountClass,
       a.normal_balance  AS normalBalance,
       s.debit_total     AS debitTotal,
       s.credit_total    AS creditTotal,
       s.entry_count     AS entryCount
FROM (
    SELECT je.account_code,
           SUM(CASE WHEN je.dr_cr = 'DEBIT'  THEN je.amount ELSE 0 END) AS debit_total,
           SUM(CASE WHEN je.dr_cr = 'CREDIT' THEN je.amount ELSE 0 END) AS credit_total,
           COUNT(*) AS entry_count
    FROM gl_journal_entry je
    WHERE je.trade_date BETWEEN @from_date AND @to_date
    GROUP BY je.account_code
) s
LEFT JOIN gl_account a ON a.account_code = s.account_code
ORDER BY s.account_code;
