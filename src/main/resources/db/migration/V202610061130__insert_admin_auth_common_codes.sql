-- ====================================================================
-- V202610061130__insert_admin_auth_common_codes.sql
-- 대상 테이블: common_code (소유 P5 · 작업 P1)
--
-- customer.role 과 customer.permissions(V202610061129)의 화면 표시명을
-- 등록한다(PH-49a-①, #561). GET /customers/me 가 role 과 permissions[] 를
-- 내보내므로 프론트가 표시명을 매핑할 행이 있어야 한다.
-- common_code 의 code 는 서버 Enum(CustomerRole · AdminPermission) 값과
-- 문자열이 일치해야 한다 (schema_reference.md common_code 절, REQ-CMN-023).
--
-- ALTER 와 같은 파일에 두지 않는 이유: MySQL DDL 은 암묵적 커밋이라, 한 파일에서
-- ALTER 가 성공하고 INSERT 가 실패하면 컬럼만 남은 채 이력이 실패로 기록된다.
--
-- sort_order 는 권한 표시 순서다. 정보계(GL) · 고객 · 감사 순으로 묶고
-- 묶음 안에서는 읽기를 쓰기보다 앞에 둔다.
-- ====================================================================

INSERT INTO common_code (code_group, code, code_name, sort_order, created_at, updated_at)
VALUES ('CUSTOMER_ROLE', 'CUSTOMER', '고객', 1, NOW(6), NOW(6)),
       ('CUSTOMER_ROLE', 'ADMIN', '관리자', 2, NOW(6), NOW(6)),
       ('ADMIN_PERMISSION', 'GL_READ', '정보계 조회', 1, NOW(6), NOW(6)),
       ('ADMIN_PERMISSION', 'GL_WRITE', '정보계 변경', 2, NOW(6), NOW(6)),
       ('ADMIN_PERMISSION', 'CUSTOMER_READ', '고객 조회', 3, NOW(6), NOW(6)),
       ('ADMIN_PERMISSION', 'CUSTOMER_WRITE', '고객 변경', 4, NOW(6), NOW(6)),
       ('ADMIN_PERMISSION', 'AUDIT_READ', '감사로그 조회', 5, NOW(6), NOW(6));
