-- 로컬 개발 및 시연용 더미 데이터
INSERT INTO customer (
  user_id,
  password_hash,
  user_name,
  birth_date,
  email,
  phone_number,
  login_failure_count,
  account_locked,
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
    NULL,
    NULL,
    NULL,
    '2026-07-10 15:00:00.000000',
    '2026-07-10 15:00:00.000000',
    '2026-07-10 15:00:00.000000',
    '2026-07-10 15:00:00.000000'
  )
ON DUPLICATE KEY UPDATE
  -- 실제 고객과 충돌하면 개인정보·비밀번호는 보존하고, 정확한 QA 시드 계정의 잠금만 복구한다.
  login_failure_count = IF(
      user_id = VALUES(user_id) AND email = VALUES(email),
      VALUES(login_failure_count),
      login_failure_count
  ),
  account_locked = IF(
      user_id = VALUES(user_id) AND email = VALUES(email),
      VALUES(account_locked),
      account_locked
  );

-- ====================================================================
-- P4 이체·원장 기능 테스트용 계좌 데이터
--
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
      AND email = 'honggildong@example.com'
);

SET @kim_customer_id = (
    SELECT customer_id
    FROM customer
    WHERE user_id = 'kimminji'
      AND email = 'kimminji@example.com'
);

SET @lee_customer_id = (
    SELECT customer_id
    FROM customer
    WHERE user_id = 'leeseojun'
      AND email = 'leeseojun@example.com'
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


-- ====================================================================
-- 계좌 데이터
-- ====================================================================

INSERT INTO account (
    account_number,
    customer_id,
    product_id,
    account_type,
    balance,
    status,
    password_hash,
    password_failure_count,
    password_locked,
    alias,
    display_order,
    withdrawal_registered,
    withdrawal_registered_at,
    opened_date,
    maturity_date,
    closed_date,
    last_transaction_at,
    version,
    created_at,
    updated_at
) VALUES

    -- ------------------------------------------------
    -- 홍길동
    -- ------------------------------------------------

    -- H1: 정상 주 출금계좌
    (
        '088100000001',
        @hong_customer_id,
        NULL,
        'DEMAND_DEPOSIT',
        100000,
        'ACTIVE',
        @account_password_hash,
        0,
        FALSE,
        '주거래 통장',
        1,
        TRUE,
        '2026-07-01 10:00:00.000000',
        '2026-07-01 09:00:00.000000',
        NULL,
        NULL,
        '2026-08-04 10:00:00.000000',
        0,
        '2026-07-01 09:00:00.000000',
        '2026-08-04 10:00:00.000000'
    ),

    -- H2: 본인 계좌 간 이체 및 역방향 동시이체
    (
        '088100000002',
        @hong_customer_id,
        NULL,
        'DEMAND_DEPOSIT',
        100000,
        'ACTIVE',
        @account_password_hash,
        0,
        FALSE,
        '비상금 통장',
        2,
        TRUE,
        '2026-07-05 10:00:00.000000',
        '2026-07-02 09:00:00.000000',
        NULL,
        NULL,
        '2026-08-02 11:00:00.000000',
        0,
        '2026-07-02 09:00:00.000000',
        '2026-08-02 11:00:00.000000'
    ),

    -- H3: 상태와 잔액은 정상이지만 출금계좌 미등록
    (
        '088100000003',
        @hong_customer_id,
        NULL,
        'DEMAND_DEPOSIT',
        100000,
        'ACTIVE',
        @account_password_hash,
        0,
        FALSE,
        '출금미등록 계좌',
        3,
        FALSE,
        NULL,
        '2026-07-03 09:00:00.000000',
        NULL,
        NULL,
        '2026-08-01 12:00:00.000000',
        0,
        '2026-07-03 09:00:00.000000',
        '2026-08-01 12:00:00.000000'
    ),

    -- H4: 잔액 부족 및 예약·자동이체 실행 실패
    (
        '088100000004',
        @hong_customer_id,
        NULL,
        'DEMAND_DEPOSIT',
        10000,
        'ACTIVE',
        @account_password_hash,
        0,
        FALSE,
        '잔액부족 계좌',
        4,
        TRUE,
        '2026-07-04 10:00:00.000000',
        '2026-07-04 09:00:00.000000',
        NULL,
        NULL,
        '2026-08-03 15:00:00.000000',
        0,
        '2026-07-04 09:00:00.000000',
        '2026-08-03 15:00:00.000000'
    ),

    -- H5: 출금계좌로 등록돼 있지만 현재 거래정지
    (
        '088100000005',
        @hong_customer_id,
        NULL,
        'DEMAND_DEPOSIT',
        100000,
        'SUSPENDED',
        @account_password_hash,
        0,
        FALSE,
        '거래정지 계좌',
        5,
        TRUE,
        '2026-07-05 10:00:00.000000',
        '2026-07-05 09:00:00.000000',
        NULL,
        NULL,
        '2026-08-01 17:00:00.000000',
        0,
        '2026-07-05 09:00:00.000000',
        '2026-08-04 09:00:00.000000'
    ),

    -- ------------------------------------------------
    -- 김민지
    -- ------------------------------------------------

    -- K1: 정상 타 고객 입출금계좌
    (
        '088100000006',
        @kim_customer_id,
        NULL,
        'DEMAND_DEPOSIT',
        100000,
        'ACTIVE',
        @account_password_hash,
        0,
        FALSE,
        '급여 통장',
        1,
        TRUE,
        '2026-07-05 12:00:00.000000',
        '2026-07-05 11:30:00.000000',
        NULL,
        NULL,
        '2026-08-04 09:00:00.000000',
        0,
        '2026-07-05 11:30:00.000000',
        '2026-08-04 09:00:00.000000'
    ),

    -- K2: 거래정지 입금계좌
    (
        '088100000007',
        @kim_customer_id,
        NULL,
        'DEMAND_DEPOSIT',
        50000,
        'SUSPENDED',
        @account_password_hash,
        0,
        FALSE,
        '거래정지 입금계좌',
        2,
        FALSE,
        NULL,
        '2026-07-06 09:00:00.000000',
        NULL,
        NULL,
        '2026-08-02 16:00:00.000000',
        0,
        '2026-07-06 09:00:00.000000',
        '2026-08-04 09:00:00.000000'
    ),

    -- K3: 해지 입금계좌
    (
        '088100000008',
        @kim_customer_id,
        NULL,
        'DEMAND_DEPOSIT',
        0,
        'CLOSED',
        @account_password_hash,
        0,
        FALSE,
        '해지 계좌',
        3,
        FALSE,
        NULL,
        '2026-06-01 09:00:00.000000',
        NULL,
        '2026-07-31 18:00:00.000000',
        NULL,
        0,
        '2026-06-01 09:00:00.000000',
        '2026-07-31 18:00:00.000000'
    ),

    -- K4: 정기적금. 즉시·예약·자동이체 입금 가능
    (
        '088300000001',
        @kim_customer_id,
        @youth_savings_product_id,
        'INSTALLMENT_SAVINGS',
        0,
        'ACTIVE',
        @account_password_hash,
        0,
        FALSE,
        '청년 희망 적금',
        4,
        FALSE,
        NULL,
        '2026-08-01 09:00:00.000000',
        '2027-08-01',
        NULL,
        NULL,
        0,
        '2026-08-01 09:00:00.000000',
        '2026-08-01 09:00:00.000000'
    ),

    -- K5: 정기예금. 일반 이체 입금 대상으로 사용 불가
    (
        '088200000001',
        @kim_customer_id,
        @basic_deposit_product_id,
        'TIME_DEPOSIT',
        100000,
        'ACTIVE',
        @account_password_hash,
        0,
        FALSE,
        '기본 정기예금',
        5,
        FALSE,
        NULL,
        '2026-08-01 10:00:00.000000',
        '2027-08-01',
        NULL,
        '2026-08-01 14:00:00.000000',
        0,
        '2026-08-01 10:00:00.000000',
        '2026-08-01 14:00:00.000000'
    ),



    -- ------------------------------------------------
    -- 이서준
    -- ------------------------------------------------

    -- L1: 동일 계좌 동시 출금 및 데드락 방지 테스트
    (
        '088100000009',
        @lee_customer_id,
        NULL,
        'DEMAND_DEPOSIT',
        100000,
        'ACTIVE',
        @account_password_hash,
        0,
        FALSE,
        '주거래 통장',
        1,
        TRUE,
        '2026-07-10 16:00:00.000000',
        '2026-07-10 15:00:00.000000',
        NULL,
        NULL,
        '2026-08-04 12:00:00.000000',
        0,
        '2026-07-10 15:00:00.000000',
        '2026-08-04 12:00:00.000000'
    )

-- 같은 QA 시드 소유자의 계좌만 초기화하며, 계좌번호가 충돌한 타인 계좌는 변경하지 않는다.
ON DUPLICATE KEY UPDATE
    balance = IF(customer_id = VALUES(customer_id), VALUES(balance), balance),
    status = IF(customer_id = VALUES(customer_id), VALUES(status), status),
    password_hash = IF(customer_id = VALUES(customer_id), VALUES(password_hash), password_hash),
    password_failure_count = IF(
        customer_id = VALUES(customer_id),
        VALUES(password_failure_count),
        password_failure_count
    ),
    password_locked = IF(customer_id = VALUES(customer_id), VALUES(password_locked), password_locked),
    alias = IF(customer_id = VALUES(customer_id), VALUES(alias), alias),
    display_order = IF(customer_id = VALUES(customer_id), VALUES(display_order), display_order),
    withdrawal_registered = IF(
        customer_id = VALUES(customer_id),
        VALUES(withdrawal_registered),
        withdrawal_registered
    ),
    withdrawal_registered_at = IF(
        customer_id = VALUES(customer_id),
        VALUES(withdrawal_registered_at),
        withdrawal_registered_at
    ),
    closed_date = IF(customer_id = VALUES(customer_id), VALUES(closed_date), closed_date),
    last_transaction_at = IF(
        customer_id = VALUES(customer_id),
        VALUES(last_transaction_at),
        last_transaction_at
    ),
    version = IF(customer_id = VALUES(customer_id), VALUES(version), version),
    updated_at = IF(customer_id = VALUES(customer_id), VALUES(updated_at), updated_at);
