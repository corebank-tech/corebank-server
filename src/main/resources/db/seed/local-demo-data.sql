-- 로컬 개발 및 시연용 더미 데이터
INSERT INTO transaction_type (code, name, sign, created_at, updated_at) VALUES
  ('TRANSFER', '이체', -1, '2026-07-28 00:00:00.000000', '2026-07-28 00:00:00.000000')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  sign = VALUES(sign),
  updated_at = VALUES(updated_at);
