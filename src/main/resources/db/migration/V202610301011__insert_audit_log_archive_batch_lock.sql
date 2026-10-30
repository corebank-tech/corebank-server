-- ====================================================================
-- V202610301011__insert_audit_log_archive_batch_lock.sql
-- P1 (PH-82, #766)
--
-- 감사 로그 WORM 적재 배치(AUDIT_LOG_ARCHIVE)가
-- 여러 인스턴스에서 중복 실행되는 것을 막는 락 행 추가
-- ====================================================================
INSERT INTO batch_execution_lock (job_name, currently_running, updated_at)
VALUES ('AUDIT_LOG_ARCHIVE', FALSE, NOW());
