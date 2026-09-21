-- ====================================================================
-- V202609220900__insert_ledger_reconciliation_batch_lock.sql
-- P4
--
-- 원장-잔액 대사 배치(LEDGER_RECONCILIATION_BATCH, #378)가
-- 여러 인스턴스에서 중복 실행되는 것을 막는 락 행 추가
-- ====================================================================
INSERT INTO batch_execution_lock (job_name, currently_running, updated_at)
VALUES ('LEDGER_RECONCILIATION_BATCH', FALSE, NOW());
