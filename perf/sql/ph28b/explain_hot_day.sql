-- PH-28b GL 정합성 점검 쿼리의 실행계획. 거래가 가장 많은 2026-09-25 기준이다. 읽기만 한다.
-- 문장은 LedgerTransactionQueryPersistenceAdapter와 GlIntegrityQueryAdapter의 상수와 같고, 바인드 값만 리터럴로 풀었다.

-- 1. 원장 거래 첫 청크. 발생 시각 창은 [직전 영업일 다음 날, T+2일)이고 거래번호 접두 범위도 같다.
--    측정 DB 휴일 테이블에서 9/24~9/26이 추석 연휴라 직전 영업일은 9/23, 하한은 9/24다.
EXPLAIN ANALYZE
SELECT le.transaction_number    AS transaction_number,
       MIN(le.transaction_type) AS transaction_type,
       MIN(le.amount)           AS amount
FROM ledger_entry le
WHERE le.trade_date = '2026-09-25'
  AND le.occurred_at >= '2026-09-24 00:00:00'
  AND le.occurred_at <  '2026-09-27 00:00:00'
  AND le.transaction_number >= '20260924'
  AND le.transaction_number <  '20260927'
  AND le.transaction_number >  ''
  AND le.transaction_type NOT IN ('OPENING', 'QA_SEED_INITIAL')
GROUP BY le.transaction_number
ORDER BY le.transaction_number
LIMIT 1000;

-- 2. 차대변 불일치 전표. 전표번호 하나로 묶어 PK 순서대로 흘려 집계한다
EXPLAIN ANALYZE
SELECT v.voucher_no, v.tx_type, v.reference_key,
       COALESCE(SUM(CASE WHEN je.dr_cr = 'DEBIT'  THEN je.amount END), 0) AS debit_total,
       COALESCE(SUM(CASE WHEN je.dr_cr = 'CREDIT' THEN je.amount END), 0) AS credit_total,
       COUNT(je.voucher_no) AS entry_count
FROM gl_voucher v
LEFT JOIN gl_journal_entry je ON je.voucher_no = v.voucher_no
WHERE v.voucher_no LIKE '20260925-%'
GROUP BY v.voucher_no
HAVING debit_total <> credit_total OR entry_count < 2
ORDER BY v.voucher_no;

-- 3. 대상 유형 전표 수
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM gl_voucher v
WHERE v.voucher_no LIKE '20260925-%'
  AND v.tx_type IN ('TRANSFER', 'PRODUCT_SUBSCRIPTION', 'REVERSAL');

-- 4. 원장 청크의 전표 합계는 참조 키 1,000개를 리터럴로 풀어야 앱과 같은 실행계획이 나온다.
--    explain_voucher_totals.sh가 1번 결과로 문장을 만들어 실행한다.
