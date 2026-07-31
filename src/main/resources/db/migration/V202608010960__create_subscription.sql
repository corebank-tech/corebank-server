-- ====================================================================
-- V202608010960__create_subscription.sql
-- 상품가입 (P3)

-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
-- ====================================================================

CREATE TABLE product_subscription (
    subscription_id          BIGINT       NOT NULL AUTO_INCREMENT,
    customer_id              BIGINT       NOT NULL,
    product_id               BIGINT       NOT NULL,
    account_id               BIGINT       NULL COMMENT '가입으로 개설된 계좌',
    withdrawal_account_id    BIGINT       NOT NULL COMMENT '초입금 출금계좌',
    subscription_amount      BIGINT       NOT NULL,
    term_months              SMALLINT     NOT NULL,
    payment_day              TINYINT      NULL COMMENT '1~28, 적립식만',
    base_rate                DECIMAL(5,2) NOT NULL,
    preferential_rate        DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    applied_rate             DECIMAL(5,2) NOT NULL,
    maturity_handling        VARCHAR(12)  NOT NULL COMMENT 'TRANSFER / RENEW',
    expected_maturity_amount BIGINT       NULL,
    status                   VARCHAR(12)  NOT NULL COMMENT 'SUCCESS / ERROR / PROCESSING',
    transaction_number       CHAR(20)     NULL COMMENT '초입금 거래번호',
    opened_date              DATE         NULL,
    maturity_date            DATE         NULL,
    subscribed_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (subscription_id),
    UNIQUE KEY uk_sub_account (account_id),
    -- PRD0301 은 product.single_account_limit = TRUE 인 상품에만 적용되므로
    -- 무조건 UNIQUE 를 걸 수 없다. 아래 인덱스로 조회 후 애플리케이션이 검증한다.
    KEY ix_sub_customer_product (customer_id, product_id, status),
    CONSTRAINT fk_sub_customer FOREIGN KEY (customer_id)           REFERENCES customer (customer_id),
    CONSTRAINT fk_sub_product  FOREIGN KEY (product_id)            REFERENCES product (product_id),
    CONSTRAINT fk_sub_account  FOREIGN KEY (account_id)            REFERENCES account (account_id),
    CONSTRAINT fk_sub_wacc     FOREIGN KEY (withdrawal_account_id) REFERENCES account (account_id)
) ENGINE=InnoDB COMMENT='상품가입';

CREATE TABLE subscription_terms_agreement (
    subscription_id BIGINT      NOT NULL,
    terms_id        BIGINT      NOT NULL,
    terms_version   VARCHAR(10) NOT NULL COMMENT '동의 시점 버전 (PRD0006)',
    read_at         DATETIME(6) NULL COMMENT '전문 열람 시각 (PRD0005)',
    agreed_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (subscription_id, terms_id),
    CONSTRAINT fk_sta_sub   FOREIGN KEY (subscription_id) REFERENCES product_subscription (subscription_id),
    CONSTRAINT fk_sta_terms FOREIGN KEY (terms_id)        REFERENCES terms (terms_id)
) ENGINE=InnoDB COMMENT='상품 약관 동의 (버전·열람 이력)';
