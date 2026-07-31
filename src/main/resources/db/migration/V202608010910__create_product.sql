-- ====================================================================
-- V202608010910__create_product.sql
-- 상품 마스터 (P3)

-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
-- ====================================================================

CREATE TABLE product (
    product_id           BIGINT       NOT NULL AUTO_INCREMENT,
    product_code         VARCHAR(20)  NOT NULL,
    product_name         VARCHAR(100) NOT NULL,
    product_group        VARCHAR(12)  NOT NULL COMMENT 'SAVINGS(정기적금) / DEPOSIT(정기예금)',
    deposit_type         VARCHAR(20)  NOT NULL COMMENT '거치식 / 적립식',
    summary              VARCHAR(200) NULL,
    description          TEXT         NULL,
    base_rate            DECIMAL(5,2) NOT NULL,
    max_rate             DECIMAL(5,2) NOT NULL,
    min_amount           BIGINT       NOT NULL,
    max_amount           BIGINT       NOT NULL,
    amount_unit          BIGINT       NOT NULL COMMENT '배수 검증 (PRD0004)',
    min_term_months      SMALLINT     NOT NULL,
    max_term_months      SMALLINT     NOT NULL,
    interest_pay_type    VARCHAR(20)  NOT NULL COMMENT '단리 / 복리',
    sale_status          VARCHAR(12)  NOT NULL COMMENT 'ON_SALE / SUSPENDED',
    sale_start_date      DATE         NULL,
    sale_end_date        DATE         NULL,
    new_flag             BOOLEAN      NOT NULL DEFAULT FALSE,
    single_account_limit BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '1인 1계좌 제한 상품 (PRD0301)',
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (product_id),
    UNIQUE KEY uk_product_code (product_code),
    KEY ix_product_group_status (product_group, sale_status),
    CONSTRAINT ck_product_amount CHECK (min_amount <= max_amount AND min_amount > 0),
    CONSTRAINT ck_product_term   CHECK (min_term_months <= max_term_months AND min_term_months > 0),
    CONSTRAINT ck_product_rate   CHECK (base_rate <= max_rate AND base_rate >= 0)
) ENGINE=InnoDB COMMENT='상품';

CREATE TABLE product_rate_tier (
    product_id  BIGINT       NOT NULL,
    term_months SMALLINT     NOT NULL,
    rate        DECIMAL(5,2) NOT NULL,
    PRIMARY KEY (product_id, term_months),
    CONSTRAINT fk_prt_product FOREIGN KEY (product_id) REFERENCES product (product_id)
) ENGINE=InnoDB COMMENT='상품 기간별 금리';

CREATE TABLE product_preferential_rate (
    product_id     BIGINT       NOT NULL,
    condition_code VARCHAR(30)  NOT NULL,
    condition_name VARCHAR(100) NOT NULL,
    rate           DECIMAL(5,2) NOT NULL,
    PRIMARY KEY (product_id, condition_code),
    CONSTRAINT fk_ppr_product FOREIGN KEY (product_id) REFERENCES product (product_id)
) ENGINE=InnoDB COMMENT='상품 우대금리';

CREATE TABLE product_terms (
    product_id BIGINT  NOT NULL,
    terms_id   BIGINT  NOT NULL,
    required   BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (product_id, terms_id),
    CONSTRAINT fk_pt_product FOREIGN KEY (product_id) REFERENCES product (product_id),
    CONSTRAINT fk_pt_terms   FOREIGN KEY (terms_id)   REFERENCES terms (terms_id)
) ENGINE=InnoDB COMMENT='상품-약관 연결';
