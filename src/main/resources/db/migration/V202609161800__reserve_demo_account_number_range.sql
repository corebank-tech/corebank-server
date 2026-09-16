-- QA 데모 계좌(홍길동)가 088100000010~088100000014를 쓸 수 있도록,
-- 입출금계좌(088/DEMAND_DEPOSIT) 채번 행의 last_sequence를 한 번만 앞당겨 예약한다.
-- R__seed_master_data.sql이 아직 이 행을 안 만든(=신선한 DB) 상태에서 V__가 먼저 돌아도
-- 안전하도록, UPDATE 대신 같은 자연키로 INSERT ... ON DUPLICATE KEY UPDATE를 쓴다.
-- (Flyway는 V__ 전체를 먼저 실행하고 R__은 그 다음에 실행하므로, 신선한 DB에서는
--  이 행이 아직 없을 수 있다 — UPDATE만 썼다면 대상 없음으로 조용히 무시된다.)
-- GREATEST로 감싸서, last_sequence가 이미 14 이상으로 앞서 있으면(실가입 등) 되돌리지 않는다.
INSERT INTO account_number_sequence
  (bank_code, account_type, product_id, product_prefix, last_sequence, created_at, updated_at)
VALUES
  ('088', 'DEMAND_DEPOSIT', NULL, '10', 14,
   '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000')
ON DUPLICATE KEY UPDATE
  last_sequence = GREATEST(last_sequence, VALUES(last_sequence)),
  updated_at = VALUES(updated_at);
