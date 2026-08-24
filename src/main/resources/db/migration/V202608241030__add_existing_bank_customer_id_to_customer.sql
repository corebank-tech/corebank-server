-- ====================================================================
-- V202608241030__add_existing_bank_customer_id_to_customer.sql
-- 고객 (P6)
--
-- 재가입 중복 판정을 계좌 단위에서 사람 단위로 올리기 위해 원장 고객
-- 식별자를 customer 에 저장한다. 지금까지는 account_number UK 충돌이
-- 사람 중복 판정을 대신하고 있었고, 그 충돌이 CMN9999(500)로 새어나갔다.
--
-- NULL 허용 UNIQUE 로 추가한다. MySQL 의 UNIQUE 인덱스는 NULL 중복을
-- 허용하므로 이미 가입한 고객 행을 백필하지 않아도 마이그레이션이 통과한다.
-- 기존 행은 원장 고객ID 를 역추적할 키가 없어 백필 자체가 불가능하다.
--
-- VARCHAR(100) 인 이유: 지금 들어오는 값은 Mock 원장의 'BANK_CUSTOMER_001'
-- 형태지만, 실서비스에서 이 자리를 대체할 CI(연계정보)가 88자다. 자리수를
-- 줄여 얻는 이득이 없으므로 email 과 같은 100 으로 맞춘다.
--
-- MySQL 은 DDL 이 암묵적 커밋이라 실패해도 롤백되지 않으므로, 한 파일이
-- 다루는 테이블을 customer 하나로 제한한다.
-- ====================================================================

ALTER TABLE customer
    ADD COLUMN existing_bank_customer_id VARCHAR(100) NULL
        COMMENT '기존 은행 원장 고객 식별자. 실서비스의 CI 대리 키 (ATH0303)'
        AFTER user_id,
    ADD UNIQUE KEY uk_customer_existing_bank_customer_id (existing_bank_customer_id);
