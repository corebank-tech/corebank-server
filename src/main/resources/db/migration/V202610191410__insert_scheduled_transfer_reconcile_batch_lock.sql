-- ====================================================================
-- V202610191410__insert_scheduled_transfer_reconcile_batch_lock.sql
-- P5 (#631)
--
-- 예약이체 재확정 배치(SCHEDULED_TRANSFER_RECONCILE)가
-- 여러 인스턴스에서 중복 실행되는 것을 막는 락 행 추가
-- ====================================================================
INSERT INTO batch_execution_lock (job_name, currently_running, updated_at)
VALUES ('SCHEDULED_TRANSFER_RECONCILE', FALSE, NOW());
