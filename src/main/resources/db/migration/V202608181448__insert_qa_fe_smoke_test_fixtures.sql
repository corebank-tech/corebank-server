-- ====================================================================
-- V202608180538__insert_qa_fe_smoke_test_fixtures.sql
-- FE 연동 테스트(#142)용 QA 고객/계좌/구독 픽스처
--
-- 실제 고객 데이터가 아닌 테스트 전용 픽스처입니다 (user_id/email에 qa_fe_test 표식).
-- 로그인 비밀번호(공통): QaFeTest1234!
-- 계좌 비밀번호(공통): 1234 (db/seed/local-demo-data.sql과 동일한 해시 재사용)
--
-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       데이터 정정이 필요하면 새 V 파일에 DELETE/UPDATE로 작성합니다.
-- ====================================================================

SET @customer_password_hash =
    '$2b$10$y9Ih8Sj4U1xWGgVWcE2OP.S5nIi7d8mjFG9gQgFp9bORXXItoBMqC';

SET @account_password_hash =
    '$2y$10$1NOtaTsHuD0rdffA3ReFKO5S0J4bHlVES6okQMYubUd0OuVFfMZXa';

-- ====================================================================
-- 고객
-- ====================================================================

INSERT INTO customer (
    user_id, password_hash, user_name, birth_date, email, phone_number,
    login_failure_count, account_locked, display_order_type,
    last_login_at, last_login_ip, previous_login_at, password_changed_at,
    joined_at, created_at, updated_at
) VALUES
    (
        'qa_fe_test_1', @customer_password_hash, 'QA FE Test 1', '1995-03-10',
        'qa-fe-test-1@corebank.test', '01000000001',
        0, FALSE, 'OPENED_DATE_ASC',
        NULL, NULL, NULL, NOW(6),
        NOW(6), NOW(6), NOW(6)
    ),
    (
        'qa_fe_test_2', @customer_password_hash, 'QA FE Test 2', '1995-03-10',
        'qa-fe-test-2@corebank.test', '01000000002',
        0, FALSE, 'OPENED_DATE_ASC',
        NULL, NULL, NULL, NOW(6),
        NOW(6), NOW(6), NOW(6)
    );

SET @qa1_customer_id = (SELECT customer_id FROM customer WHERE user_id = 'qa_fe_test_1');
SET @qa2_customer_id = (SELECT customer_id FROM customer WHERE user_id = 'qa_fe_test_2');

-- ====================================================================
-- 회원가입 약관 동의 (SIGNUP, is_required=TRUE 대상만)
-- ====================================================================

INSERT INTO customer_terms_agreement (customer_id, terms_id, agreed_at)
SELECT c.customer_id, t.terms_id, NOW(6)
FROM (SELECT @qa1_customer_id AS customer_id UNION ALL SELECT @qa2_customer_id) c
JOIN terms t ON t.terms_code IN ('TERMS_SERVICE', 'TERMS_PRIVACY') AND t.version = 'v1.0';

-- ====================================================================
-- 이체한도 (P1) — 향후 이체 API(POST /transfers, #127 해결 후) 검증에 필요
-- ====================================================================

INSERT INTO transfer_limit (customer_id, one_time_limit, daily_limit, version, updated_at)
VALUES
    (@qa1_customer_id, 5000000, 10000000, 0, NOW(6)),
    (@qa2_customer_id, 5000000, 10000000, 0, NOW(6));

-- ====================================================================
-- 계좌번호 채번
--
-- 하드코딩된 계좌번호 대신 account_number_sequence를 실제로 증가시켜 발급한다.
-- (prod의 채번 현황을 마이그레이션 시점에 알 수 없으므로, 향후 앱이 채번하는
--  실제 계좌번호와 충돌하지 않도록 동일한 채번 규칙을 그대로 사용)
-- 계좌번호 구성: 은행코드 3자리 + 상품 Prefix 2자리 + 일련번호 7자리
-- ====================================================================

INSERT INTO account_number_sequence
    (bank_code, account_type, product_id, product_prefix, last_sequence, created_at, updated_at)
VALUES
    ('088', 'DEMAND_DEPOSIT', NULL, '10', 0, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE updated_at = updated_at;

UPDATE account_number_sequence
SET last_sequence = last_sequence + 3, updated_at = NOW(6)
WHERE bank_code = '088' AND product_prefix = '10';

SET @dd_seq_end = (
    SELECT last_sequence FROM account_number_sequence
    WHERE bank_code = '088' AND product_prefix = '10'
);

SET @qa1_dd_account_number  = CONCAT('088', '10', LPAD(@dd_seq_end - 2, 7, '0'));
SET @qa2_dd_account_number  = CONCAT('088', '10', LPAD(@dd_seq_end - 1, 7, '0'));
SET @qa1_td_account_number  = CONCAT('088', '10', LPAD(@dd_seq_end,     7, '0'));

-- ====================================================================
-- 계좌
--
-- QA1-A: qa_fe_test_1 소유 입출금계좌. 이체 출금계좌로 사용 가능하도록
--        withdrawal_registered=TRUE, 향후 이체 검증을 감안해 잔액 충분히 확보.
-- QA2-A: qa_fe_test_2 소유 입출금계좌. QA1-A → QA2-A 이체 테스트의 입금 대상.
-- QA1-B: qa_fe_test_1이 가입한 정기예금 계좌 (product_subscription 연결용).
-- ====================================================================

INSERT INTO account (
    account_number, customer_id, product_id, account_type, balance, status,
    password_hash, password_failure_count, password_locked,
    alias, display_order, withdrawal_registered, withdrawal_registered_at,
    opened_date, maturity_date, closed_date, last_transaction_at,
    version, created_at, updated_at
) VALUES
    (
        @qa1_dd_account_number, @qa1_customer_id, NULL, 'DEMAND_DEPOSIT',
        5000000, 'ACTIVE', @account_password_hash, 0, FALSE,
        'QA 입출금계좌', 1, TRUE, NOW(6),
        CURDATE(), NULL, NULL, NULL,
        0, NOW(6), NOW(6)
    ),
    (
        @qa2_dd_account_number, @qa2_customer_id, NULL, 'DEMAND_DEPOSIT',
        3000000, 'ACTIVE', @account_password_hash, 0, FALSE,
        'QA 입출금계좌', 1, TRUE, NOW(6),
        CURDATE(), NULL, NULL, NULL,
        0, NOW(6), NOW(6)
    );

SET @youth_savings_product_id = (SELECT product_id FROM product WHERE product_code = 'PRD_YOUTH_SAVE');
SET @basic_deposit_product_id = (SELECT product_id FROM product WHERE product_code = 'PRD_BASIC_DEP');

INSERT INTO account (
    account_number, customer_id, product_id, account_type, balance, status,
    password_hash, password_failure_count, password_locked,
    alias, display_order, withdrawal_registered, withdrawal_registered_at,
    opened_date, maturity_date, closed_date, last_transaction_at,
    version, created_at, updated_at
) VALUES (
    @qa1_td_account_number, @qa1_customer_id, @basic_deposit_product_id, 'TIME_DEPOSIT',
    3000000, 'ACTIVE', @account_password_hash, 0, FALSE,
    'QA 정기예금', 2, FALSE, NULL,
    CURDATE(), DATE_ADD(CURDATE(), INTERVAL 6 MONTH), NULL, NULL,
    0, NOW(6), NOW(6)
);

SET @qa1_dd_account_id = (SELECT account_id FROM account WHERE account_number = @qa1_dd_account_number);
SET @qa2_dd_account_id = (SELECT account_id FROM account WHERE account_number = @qa2_dd_account_number);
SET @qa1_td_account_id = (SELECT account_id FROM account WHERE account_number = @qa1_td_account_number);

-- ====================================================================
-- 상품가입 — GET /product-subscriptions/{id} 조회 대상
-- ====================================================================

INSERT INTO product_subscription (
    customer_id, product_id, account_id, withdrawal_account_id,
    subscription_amount, term_months, payment_day,
    base_rate, preferential_rate, applied_rate,
    maturity_handling, expected_maturity_amount, status,
    transaction_number, opened_date, maturity_date, subscribed_at
) VALUES (
    @qa1_customer_id, @basic_deposit_product_id, @qa1_td_account_id, @qa1_dd_account_id,
    3000000, 6, NULL,
    2.80, 0.00, 2.80,
    'TRANSFER', 3042000, 'SUCCESS',
    NULL, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 6 MONTH), NOW(6)
);

SET @qa1_subscription_id = (SELECT subscription_id FROM product_subscription WHERE account_id = @qa1_td_account_id);
SET @deposit_terms_id = (SELECT terms_id FROM terms WHERE terms_code = 'TERMS_DEPOSIT' AND version = 'v1.0');

INSERT INTO subscription_terms_agreement (subscription_id, terms_id, terms_version, read_at, agreed_at)
VALUES (@qa1_subscription_id, @deposit_terms_id, 'v1.0', NOW(6), NOW(6));

-- ====================================================================
-- 자동이체 — GET /auto-transfers, GET /auto-transfers/executions 조회 대상
-- (등록·변경·해지 등 상태변경 API는 오늘 테스트 범위 밖이라 데이터만 준비)
-- ====================================================================

INSERT INTO auto_transfer (
    customer_id, withdrawal_account_id, deposit_account_number, payee_name,
    amount, cycle_months, transfer_day, start_date, end_date, next_execution_date,
    my_passbook_memo, recipient_passbook_memo, status, registered_at, updated_at, version
) VALUES (
    @qa1_customer_id, @qa1_dd_account_id, @qa2_dd_account_number, 'QA FE Test 2',
    100000, 1, 25, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), DATE_ADD(CURDATE(), INTERVAL 1 MONTH),
    '자동이체', '용돈', 'NORMAL', NOW(6), NOW(6), 0
);

SET @qa1_auto_transfer_id = (
    SELECT auto_transfer_id FROM auto_transfer
    WHERE customer_id = @qa1_customer_id AND withdrawal_account_id = @qa1_dd_account_id
);

INSERT INTO auto_transfer_execution (
    auto_transfer_id, execution_date, amount, status, transaction_number, failure_reason, executed_at
) VALUES (
    @qa1_auto_transfer_id, DATE_SUB(CURDATE(), INTERVAL 1 MONTH), 100000, 'SUCCESS',
    'QAAUTOEXEC00000001', NULL, DATE_SUB(NOW(6), INTERVAL 1 MONTH)
);
