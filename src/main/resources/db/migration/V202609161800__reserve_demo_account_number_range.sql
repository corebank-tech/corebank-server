-- QA 데모 계좌(홍길동)가 088100000010~088100000014를 쓸 수 있도록,
-- 입출금계좌(088/DEMAND_DEPOSIT) 채번 행의 last_sequence를 한 번만 앞당겨 예약한다.
-- GREATEST로 감싸서, last_sequence가 이미 14 이상으로 앞서 있으면(실가입 등) 되돌리지 않는다.
UPDATE account_number_sequence
SET last_sequence = GREATEST(last_sequence, 14)
WHERE bank_code = '088'
  AND account_type = 'DEMAND_DEPOSIT'
  AND product_id IS NULL;
