INSERT INTO transaction_type (code, name, sign, created_at, updated_at) VALUES
  ('DEPOSIT', '입금', 1, '2026-07-28 00:00:00.000000', '2026-07-28 00:00:00.000000'),
  ('WITHDRAWAL', '출금', -1, '2026-07-28 00:00:00.000000', '2026-07-28 00:00:00.000000')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  sign = VALUES(sign),
  updated_at = VALUES(updated_at);
