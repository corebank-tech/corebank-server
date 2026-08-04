-- ====================================================================
-- V202608041204__create_account_number_sequence.sql
-- 계좌번호 채번 테이블 생성 (P2)
--
-- 은행코드·계좌유형·상품별 계좌번호 발급 규칙과
-- 마지막 발급 일련번호를 관리한다.
--
-- 계좌번호 구성:
--   은행코드 3자리 + 상품 Prefix 2자리 + 일련번호 7자리
--
-- 계좌번호 발급 시 대상 채번 행을 비관적 락으로 조회하며,
-- 채번값 갱신과 account 생성은 같은 트랜잭션에서 처리한다.
--
-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새로운 V 파일에 작성합니다.
-- ====================================================================
CREATE TABLE `account_number_sequence` (
    `sequence_id` BIGINT NOT NULL AUTO_INCREMENT,

    `bank_code` CHAR(3) NOT NULL
        COMMENT '계좌번호에 포함되는 은행코드 3자리',

    `account_type` VARCHAR(24) NOT NULL
        COMMENT 'DEMAND_DEPOSIT / TIME_DEPOSIT / INSTALLMENT_SAVINGS',

    `product_id` BIGINT NULL
        COMMENT '입출금계좌는 NULL, 예·적금계좌는 상품 식별자',

    `product_prefix` CHAR(2) NOT NULL
        COMMENT '계좌번호에 포함되는 상품별 Prefix 2자리',

    `last_sequence` BIGINT NOT NULL DEFAULT 0
        COMMENT '마지막으로 발급한 7자리 일련번호',

    `created_at` DATETIME(6) NOT NULL,

    `updated_at` DATETIME(6) NOT NULL,

    PRIMARY KEY (`sequence_id`),

    UNIQUE KEY `uk_account_number_sequence_prefix`
        (`bank_code`, `product_prefix`),

    UNIQUE KEY `uk_account_number_sequence_product`
        (`bank_code`, `product_id`),

    KEY `ix_account_number_sequence_lookup`
        (`bank_code`, `account_type`, `product_id`),

    KEY `ix_account_number_sequence_product`
        (`product_id`),

    CONSTRAINT `fk_account_number_sequence_product`
        FOREIGN KEY (`product_id`)
        REFERENCES `product` (`product_id`),

    CONSTRAINT `ck_account_number_sequence_bank_code`
        CHECK (`bank_code` REGEXP '^[0-9]{3}$'),

    CONSTRAINT `ck_account_number_sequence_prefix`
        CHECK (`product_prefix` REGEXP '^[0-9]{2}$'),

    CONSTRAINT `ck_account_number_sequence_type`
        CHECK (
            `account_type` IN (
                'DEMAND_DEPOSIT',
                'TIME_DEPOSIT',
                'INSTALLMENT_SAVINGS'
            )
        ),

    CONSTRAINT `ck_account_number_sequence_product`
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

    CONSTRAINT `ck_account_number_sequence_value`
        CHECK (
            `last_sequence` BETWEEN 0 AND 9999999
        )
)
ENGINE = InnoDB
COMMENT = '은행·상품별 계좌번호 채번';