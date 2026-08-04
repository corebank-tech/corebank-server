-- ====================================================================
-- V202608040930__add_account_constraints.sql
-- account 추가 CHECK 제약조건 생성
--
-- 기존 생성 항목:
--   UNIQUE uk_account_number
--   INDEX  ix_account_customer
--   INDEX  ix_account_withdrawal
--   CHECK  ck_account_balance
--   CHECK  ck_account_number
--
-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오.
--       변경은 새로운 V 파일에 작성합니다.
-- ====================================================================

ALTER TABLE `account`

    ADD CONSTRAINT `ck_account_type`
        CHECK (
            `account_type` IN (
                'DEMAND_DEPOSIT',
                'TIME_DEPOSIT',
                'INSTALLMENT_SAVINGS'
            )
        ),

    ADD CONSTRAINT `ck_account_status`
        CHECK (
            `status` IN (
                'ACTIVE',
                'SUSPENDED',
                'CLOSED'
            )
        ),

    ADD CONSTRAINT `ck_account_password_lock_state`
        CHECK (
            (
                `password_failure_count` BETWEEN 0 AND 4
                AND `password_locked` = FALSE
            )
            OR
            (
                `password_failure_count` = 5
                AND `password_locked` = TRUE
            )
        ),

    ADD CONSTRAINT `ck_account_withdrawal_registered`
        CHECK (
            `withdrawal_registered` IN (FALSE, TRUE)
        ),

    ADD CONSTRAINT `ck_account_product`
        CHECK (
            (
                `account_type` = 'DEMAND_DEPOSIT'
                AND `product_id` IS NULL
            )
            OR
            (
                `account_type` IN (
                    'TIME_DEPOSIT',
                    'INSTALLMENT_SAVINGS'
                )
                AND `product_id` IS NOT NULL
            )
        ),

    ADD CONSTRAINT `ck_account_maturity`
        CHECK (
            (
                `account_type` = 'DEMAND_DEPOSIT'
                AND `maturity_date` IS NULL
            )
            OR
            (
                `account_type` IN (
                    'TIME_DEPOSIT',
                    'INSTALLMENT_SAVINGS'
                )
                AND `maturity_date` IS NOT NULL
                AND `maturity_date` >= `opened_date`
            )
        ),

    ADD CONSTRAINT `ck_account_withdrawal_type`
        CHECK (
            `withdrawal_registered` = FALSE
            OR `account_type` = 'DEMAND_DEPOSIT'
        ),

    ADD CONSTRAINT `ck_account_withdrawal_registered_at`
        CHECK (
            (
                `withdrawal_registered` = FALSE
                AND `withdrawal_registered_at` IS NULL
            )
            OR
            (
                `withdrawal_registered` = TRUE
                AND `withdrawal_registered_at` IS NOT NULL
            )
        ),

    ADD CONSTRAINT `ck_account_closed_date`
        CHECK (
            (
                `status` = 'CLOSED'
                AND `closed_date` IS NOT NULL
                AND `closed_date` >= `opened_date`
            )
            OR
            (
                `status` <> 'CLOSED'
                AND `closed_date` IS NULL
            )
        );