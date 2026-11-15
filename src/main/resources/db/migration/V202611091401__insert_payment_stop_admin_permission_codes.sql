-- ====================================================================
-- V202611091401__insert_payment_stop_admin_permission_codes.sql
-- 대상 테이블: common_code (ADMIN_PERMISSION 그룹)
--
-- 직전 파일에서 CHECK 에 넣은 지급정지 권한 3종의 공통코드다.
-- code 는 서버 AdminPermission enum 값과 문자열이 같아야 한다(REQ-CMN-023).
-- ====================================================================

INSERT INTO common_code (code_group, code, code_name, sort_order, created_at, updated_at)
VALUES ('ADMIN_PERMISSION', 'PAYMENT_STOP_READ', '지급정지 조회', 6, NOW(6), NOW(6)),
       ('ADMIN_PERMISSION', 'PAYMENT_STOP_WRITE', '지급정지 변경', 7, NOW(6), NOW(6)),
       ('ADMIN_PERMISSION', 'PAYMENT_STOP_APPROVE', '지급정지 해제 승인', 8, NOW(6), NOW(6));
