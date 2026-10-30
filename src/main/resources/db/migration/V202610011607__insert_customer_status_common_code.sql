-- ====================================================================
-- V202610011607__insert_customer_status_common_code.sql
-- 공통코드 (P6)
--
-- customer.status(V202610011606)의 화면 표시명을 등록한다(#450).
-- common_code 의 code 는 서버 Enum(CustomerStatus) 값과 문자열이 일치해야 한다
-- (schema_reference.md common_code 절, REQ-CMN-023).
--
-- ALTER 와 같은 파일에 두지 않는 이유: MySQL DDL 은 암묵적 커밋이라, 한 파일에서
-- ALTER 가 성공하고 INSERT 가 실패하면 컬럼만 남은 채 이력이 실패로 기록된다.
-- ====================================================================

INSERT INTO common_code (code_group, code, code_name, sort_order, created_at, updated_at)
VALUES ('CUSTOMER_STATUS', 'ACTIVE', '정상', 1, NOW(6), NOW(6)),
       ('CUSTOMER_STATUS', 'SUSPENDED', '이용정지', 2, NOW(6), NOW(6));
