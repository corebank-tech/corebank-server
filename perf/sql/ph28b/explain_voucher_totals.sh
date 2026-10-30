#!/usr/bin/env bash
# explain_hot_day.sql 4번. 2026-09-25 원장 첫 청크의 거래번호 1,000개로 전표 합계 쿼리를 만들어 EXPLAIN ANALYZE 한다. 읽기만 한다.
# 사용: CONTAINER=ph28b-perf-mysql ./explain_voucher_totals.sh
set -euo pipefail
CONTAINER=${CONTAINER:-ph28b-perf-mysql}
mysql_exec() { docker exec -i "$CONTAINER" mysql -uroot -plocalpw -N minicore 2>/dev/null; }

keys=$(mysql_exec <<'SQL' | awk '{printf "%s'\''%s'\''", (NR>1?",":""), $1}'
SELECT le.transaction_number
FROM ledger_entry le
WHERE le.trade_date = '2026-09-25'
  AND le.occurred_at >= '2026-09-23 00:00:00' AND le.occurred_at < '2026-09-27 00:00:00'
  AND le.transaction_number >= '20260923' AND le.transaction_number < '20260927'
  AND le.transaction_type NOT IN ('OPENING', 'QA_SEED_INITIAL')
GROUP BY le.transaction_number
ORDER BY le.transaction_number
LIMIT 1000;
SQL
)

mysql_exec <<SQL
EXPLAIN ANALYZE
SELECT v.tx_type, v.reference_key,
       COALESCE(SUM(CASE WHEN je.dr_cr = 'DEBIT'  THEN je.amount END), 0) AS debit_total,
       COALESCE(SUM(CASE WHEN je.dr_cr = 'CREDIT' THEN je.amount END), 0) AS credit_total
FROM gl_voucher v
LEFT JOIN gl_journal_entry je ON je.voucher_no = v.voucher_no
WHERE v.tx_type IN ('TRANSFER', 'PRODUCT_SUBSCRIPTION', 'REVERSAL')
  AND v.reference_key IN ($keys)
  AND v.voucher_no LIKE '20260925-%'
GROUP BY v.voucher_no, v.tx_type, v.reference_key;
SQL
