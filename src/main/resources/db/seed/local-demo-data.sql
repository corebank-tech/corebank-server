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

-- ====================================================================
-- P4 이체·원장 기능 테스트용 계좌 데이터
--
-- 모든 계좌 테스트 비밀번호: 1234
-- 계좌번호 규칙:
--   은행코드 3자리 + 상품 Prefix 2자리 + 일련번호 7자리
--
-- Prefix:
--   10: 입출금계좌
--   20: 정기예금
--   30: 정기적금
--
-- 주의:
--   account.balance는 조회용 캐시이며,
--   P4의 초기 ledger_entry 합계와 반드시 일치해야 한다.
-- ====================================================================

SET @account_password_hash =
    '$2y$10$1NOtaTsHuD0rdffA3ReFKO5S0J4bHlVES6okQMYubUd0OuVFfMZXa';

SET @hong_customer_id = (
    SELECT customer_id
    FROM customer
    WHERE user_id = 'honggildong'
);

SET @kim_customer_id = (
    SELECT customer_id
    FROM customer
    WHERE user_id = 'kimminji'
);

SET @lee_customer_id = (
    SELECT customer_id
    FROM customer
    WHERE user_id = 'leeseojun'
);

SET @youth_savings_product_id = (
    SELECT product_id
    FROM product
    WHERE product_code = 'PRD_YOUTH_SAVE'
);

SET @basic_deposit_product_id = (
    SELECT product_id
    FROM product
    WHERE product_code = 'PRD_BASIC_DEP'
);

-- ====================================================================
-- 계좌번호 채번 기준 데이터
--
-- 이미 더 큰 번호까지 발급된 경우 last_sequence를 낮추지 않는다.
-- ====================================================================

INSERT INTO account_number_sequence (
    bank_code,
    account_type,
    product_id,
    product_prefix,
    last_sequence,
    created_at,
    updated_at
) VALUES
    (
        '088',
        'DEMAND_DEPOSIT',
        NULL,
        '10',
        9,
        '2026-08-01 00:00:00.000000',
        '2026-08-05 00:00:00.000000'
    ),
    (
        '088',
        'TIME_DEPOSIT',
        @basic_deposit_product_id,
        '20',
        1,
        '2026-08-01 00:00:00.000000',
        '2026-08-05 00:00:00.000000'
    ),
    (
        '088',
        'INSTALLMENT_SAVINGS',
        @youth_savings_product_id,
        '30',
        1,
        '2026-08-01 00:00:00.000000',
        '2026-08-05 00:00:00.000000'
    )
ON DUPLICATE KEY UPDATE
    last_sequence = GREATEST(
        last_sequence,
        VALUES(last_sequence)
    ),
    updated_at = VALUES(updated_at);