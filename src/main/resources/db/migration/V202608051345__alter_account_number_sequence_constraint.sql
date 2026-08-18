-- ====================================================================
-- V202608051345__alter_account_number_sequence_constraint.sql
-- account number sequence UNIQUE 제약조건 수정
--
-- 삭제 항목:
--   UNIQUE uk_account_number_sequence_product
--
-- 생성 항목:
--   UNIQUE uk_account_number_sequence_rule
--
-- 변경 목적:
--   같은 은행에서 product_id가 NULL인 입출금계좌 채번 규칙이
--   여러 행 등록되는 것을 방지한다.
--
-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오.
--       변경은 새로운 V 파일에 작성합니다.
-- ====================================================================
ALTER TABLE `account_number_sequence`
    DROP INDEX `uk_account_number_sequence_product`,
    ADD UNIQUE KEY `uk_account_number_sequence_rule`
        (
            `bank_code`,
            (COALESCE(`product_id`, 0))
        );