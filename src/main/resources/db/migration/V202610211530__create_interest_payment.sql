-- PH-14 이자 지급 이력 (#687)
-- 한 계좌의 만기/중도해지 이자 지급은 최대 1회.
-- 원장 및 GL과 동일한 트랜잭션에서 기록하며 실패 시 함께 롤백한다.

CREATE TABLE interest_payment (
    interest_payment_id BIGINT       NOT NULL AUTO_INCREMENT,
    account_id          BIGINT       NOT NULL,
    payment_reason      VARCHAR(12)  NOT NULL,
    gross_amount        BIGINT       NOT NULL,
    tax_amount          BIGINT       NOT NULL,
    net_amount          BIGINT       NOT NULL,
    transaction_number  CHAR(20)     NULL,
    trade_date          DATE         NULL,
    paid_at             DATETIME(6)  NOT NULL,

    PRIMARY KEY (interest_payment_id),
    UNIQUE KEY uk_interest_payment_account (account_id),
    UNIQUE KEY uk_interest_payment_tx (transaction_number),

    CONSTRAINT ck_interest_payment_amount
        CHECK (
            gross_amount >= 0
            AND tax_amount >= 0
            AND tax_amount <= gross_amount
            AND net_amount = gross_amount - tax_amount
        ),

    CONSTRAINT ck_interest_payment_ledger
        CHECK (
            (net_amount = 0
                AND transaction_number IS NULL
                AND trade_date IS NULL)
            OR
            (net_amount > 0
                AND transaction_number IS NOT NULL
                AND trade_date IS NOT NULL)
        )
) ENGINE=InnoDB COMMENT='예적금 이자 지급 이력 (PH-14, #687)';
