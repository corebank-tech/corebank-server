-- ====================================================================
-- V202608010920__create_account.sql
-- 계좌 (P2)

-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
-- ====================================================================

CREATE TABLE account (
    account_id                BIGINT      NOT NULL AUTO_INCREMENT,
    account_number            CHAR(12)    NOT NULL COMMENT '하이픈 없는 숫자 12자리',
    customer_id               BIGINT      NOT NULL,
    product_id                BIGINT      NULL COMMENT '입출금계좌는 NULL',
    account_type              VARCHAR(24) NOT NULL COMMENT 'DEMAND_DEPOSIT / TIME_DEPOSIT / INSTALLMENT_SAVINGS',
    balance                   BIGINT      NOT NULL DEFAULT 0 COMMENT '원장 대사 대상',
    status                    VARCHAR(12) NOT NULL COMMENT 'ACTIVE / SUSPENDED / CLOSED',
    password_hash             CHAR(60)    NOT NULL COMMENT '계좌비밀번호 BCrypt',
    password_failure_count    TINYINT     NOT NULL DEFAULT 0,
    password_locked           BOOLEAN     NOT NULL DEFAULT FALSE COMMENT 'APW0101',
    -- account_preference 흡수: 계좌당 1행이고 생명주기가 같다
    alias                     VARCHAR(24) NULL COMMENT '계좌별명. 한글 12자·영숫자 24자 (ACC0001)',
    display_order             INT         NULL COMMENT '사용자 지정 표시순서',
    -- withdrawal_account 흡수: 존재 여부만 의미하므로 플래그로 충분
    withdrawal_registered     BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '응답 withdrawalAccountRegistered',
    withdrawal_registered_at  DATETIME(6) NULL,
    opened_date               DATE        NOT NULL,
    maturity_date             DATE        NULL,
    closed_date               DATE        NULL,
    last_transaction_at       DATETIME(6) NULL,
    version                   BIGINT      NOT NULL DEFAULT 0 COMMENT '낙관적 락',
    created_at                DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_id),
    UNIQUE KEY uk_account_number (account_number),
    KEY ix_account_customer (customer_id, status),
    KEY ix_account_withdrawal (customer_id, withdrawal_registered),
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id),
    CONSTRAINT fk_account_product  FOREIGN KEY (product_id)  REFERENCES product (product_id),
    CONSTRAINT ck_account_balance CHECK (balance >= 0),
    CONSTRAINT ck_account_number  CHECK (account_number REGEXP '^[0-9]{12}$')
) ENGINE=InnoDB COMMENT='계좌 (별명·표시순서·출금등록 흡수)';
