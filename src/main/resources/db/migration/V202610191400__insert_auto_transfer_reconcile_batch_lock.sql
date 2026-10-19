-- ====================================================================
-- V202610191400__insert_auto_transfer_reconcile_batch_lock.sql
-- P5 (#365)
--
-- 자동이체 재확정 배치(AUTO_TRANSFER_RECONCILE)가
-- 여러 인스턴스에서 중복 실행되는 것을 막는 락 행 추가
-- ====================================================================
INSERT INTO batch_execution_lock (job_name, currently_running, updated_at)
VALUES ('AUTO_TRANSFER_RECONCILE', FALSE, NOW());
