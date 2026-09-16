-- ====================================================================
-- V202609081402__insert_idempotency_cleanup_batch_lock.sql
-- P5
--
-- 멱등키 정리 배치(IDEMPOTENCY_KEY_CLEANUP)가
-- 여러 인스턴스에서 중복 실행되는 것을 막는 락 행 추가
-- ====================================================================
INSERT INTO batch_execution_lock (job_name, currently_running, updated_at)
VALUES ('IDEMPOTENCY_KEY_CLEANUP', FALSE, NOW());
