-- 로컬 개발 및 시연용 더미 데이터
-- 테스트 로그인 비밀번호: honggildong=Hong1234!, kimminji=Minji1234!, leeseojun=Seojun1234!
INSERT INTO customer (
  user_id,
  password_hash,
  user_name,
  birth_date,
  email,
  phone_number,
  login_failure_count,
  account_locked,
  display_order_type,
  last_login_at,
  last_login_ip,
  previous_login_at,
  password_changed_at,
  joined_at,
  created_at,
  updated_at
) VALUES
  (
    'honggildong',
    '$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq',
    '홍길동',
    '1990-01-15',
    'honggildong@example.com',
    '01012345678',
    0,
    FALSE,
    'OPENED_DATE_ASC',
    '2026-08-03 09:30:00.000000',
    '127.0.0.1',
    '2026-08-01 18:20:00.000000',
    '2026-07-01 10:00:00.000000',
    '2026-07-01 10:00:00.000000',
    '2026-07-01 10:00:00.000000',
    '2026-08-03 09:30:00.000000'
  ),
  (
    'kimminji',
    '$2a$10$tMnneNS3bgqYvVD4fPEbNOjEoVXt2DVKAlgfsRK0g76Wkwzds1gPO',
    '김민지',
    '1994-05-22',
    'kimminji@example.com',
    '01023456789',
    0,
    FALSE,
    'CUSTOM',
    '2026-08-04 08:45:00.000000',
    '192.168.0.10',
    '2026-08-02 14:10:00.000000',
    '2026-07-05 11:30:00.000000',
    '2026-07-05 11:30:00.000000',
    '2026-07-05 11:30:00.000000',
    '2026-08-04 08:45:00.000000'
  ),
  (
    'leeseojun',
    '$2a$10$WfjYUrSpgUlNBf/AVBW1A.4J7WW2dURVwDJnzyTlLHSlRJzpyOczq',
    '이서준',
    '1987-11-03',
    'leeseojun@example.com',
    '01034567890',
    0,
    FALSE,
    'OPENED_DATE_ASC',
    NULL,
    NULL,
    NULL,
    '2026-07-10 15:00:00.000000',
    '2026-07-10 15:00:00.000000',
    '2026-07-10 15:00:00.000000',
    '2026-07-10 15:00:00.000000'
  )
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  user_name = VALUES(user_name),
  birth_date = VALUES(birth_date),
  email = VALUES(email),
  phone_number = VALUES(phone_number),
  login_failure_count = VALUES(login_failure_count),
  account_locked = VALUES(account_locked),
  display_order_type = VALUES(display_order_type),
  last_login_at = VALUES(last_login_at),
  last_login_ip = VALUES(last_login_ip),
  previous_login_at = VALUES(previous_login_at),
  password_changed_at = VALUES(password_changed_at),
  joined_at = VALUES(joined_at),
  updated_at = VALUES(updated_at);

-- INSERT INTO transaction_type (code, name, sign, created_at, updated_at) VALUES
--   ('TRANSFER', '이체', -1, '2026-07-28 00:00:00.000000', '2026-07-28 00:00:00.000000')
-- ON DUPLICATE KEY UPDATE
--   name = VALUES(name),
--   sign = VALUES(sign),
--   updated_at = VALUES(updated_at);
