-- 사기 확정 거절을 일반 관리자 쓰기 권한과 분리한다.
ALTER TABLE admin_permission DROP CHECK ck_admin_permission;
ALTER TABLE admin_permission
    ADD CONSTRAINT ck_admin_permission
        CHECK (permission COLLATE utf8mb4_0900_as_cs IN (
            'GL_READ','GL_WRITE','CUSTOMER_READ','CUSTOMER_WRITE','AUDIT_READ',
            'PAYMENT_STOP_READ','PAYMENT_STOP_WRITE','PAYMENT_STOP_APPROVE','FDS_FRAUD_CONFIRM'
        ));
