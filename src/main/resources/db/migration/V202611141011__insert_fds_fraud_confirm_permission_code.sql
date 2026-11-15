-- 관리자 권한 Enum·CHECK와 동일한 사기 확정 권한 공통코드를 등록한다.
INSERT INTO common_code (code_group, code, code_name, sort_order, created_at, updated_at)
VALUES ('ADMIN_PERMISSION', 'FDS_FRAUD_CONFIRM', 'FDS 사기 확정', 9, NOW(6), NOW(6));
