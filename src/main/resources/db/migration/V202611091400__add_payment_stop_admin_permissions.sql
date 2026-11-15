-- ====================================================================
-- V202611091400__add_payment_stop_admin_permissions.sql
-- 대상 테이블: admin_permission (CHECK 재생성)
--
-- 이의제기 AI 심사 지원(D-36)의 관리자 권한 3종을 더한다(Plan D14).
--   PAYMENT_STOP_READ    지급정지 사건과 이의제기 조회
--   PAYMENT_STOP_WRITE   사건 등록, 이의제기 접수, 판정 작성
--   PAYMENT_STOP_APPROVE 보류를 줄이는 판정(수용, 일부 지급정지)의 승인. 작성자와 다른 사람이 한다
-- AdminPermission enum, 이 CHECK, common_code ADMIN_PERMISSION 그룹 세 곳을 함께 바꾼다.
-- common_code INSERT 는 다음 파일에 둔다. MySQL DDL 은 암묵적 커밋이라 한 파일에 섞지 않는다.
-- ====================================================================

ALTER TABLE admin_permission
    DROP CHECK ck_admin_permission;

ALTER TABLE admin_permission
    ADD CONSTRAINT ck_admin_permission
        CHECK (permission COLLATE utf8mb4_0900_as_cs IN (
            'GL_READ', 'GL_WRITE', 'CUSTOMER_READ', 'CUSTOMER_WRITE', 'AUDIT_READ',
            'PAYMENT_STOP_READ', 'PAYMENT_STOP_WRITE', 'PAYMENT_STOP_APPROVE'
        ));
