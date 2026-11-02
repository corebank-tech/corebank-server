-- ====================================================================
-- V202610311001__insert_audit_integrity_check_batch_lock.sql
-- P1 (PH-82, #766)
--
-- 감사 무결성 검증 배치(AUDIT_INTEGRITY_CHECK)가
-- 여러 인스턴스에서 중복 실행되는 것을 막는 락 행 추가
-- ====================================================================
INSERT INTO batch_execution_lock (job_name, currently_running, updated_at)
VALUES ('AUDIT_INTEGRITY_CHECK', FALSE, NOW());
