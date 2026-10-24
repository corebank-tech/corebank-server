-- PH-28b 60b 오탐 기준선 전 확인. 거래일이 비어 있는 원장 행은 어느 거래일 대사에도 걸리지 않는다.
-- 그 거래의 전표는 자기 거래일에서 짝이 없어 고아 전표로 셀 수 있으므로, 기준선을 읽기 전에 규모를 먼저 본다. 읽기만 한다.

-- 1. 거래일이 비어 있는 원장 행 — 유형별
SELECT transaction_type, COUNT(*) AS ledger_rows, COUNT(DISTINCT transaction_number) AS transactions
FROM ledger_entry
WHERE trade_date IS NULL
GROUP BY transaction_type
ORDER BY transaction_type;

-- 2. 그중 전표가 있는 거래 — 전표 거래일별. 여기 나오는 건수만큼 그 거래일의 고아 전표 수가 늘어난다
SELECT v.trade_date, v.tx_type, COUNT(*) AS vouchers
FROM gl_voucher v
JOIN (SELECT DISTINCT transaction_number FROM ledger_entry WHERE trade_date IS NULL) le
  ON le.transaction_number = v.reference_key
GROUP BY v.trade_date, v.tx_type
ORDER BY v.trade_date, v.tx_type;
