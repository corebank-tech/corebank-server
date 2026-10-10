-- ====================================================================
-- V202610091903__insert_terms_view_history_cleanup_batch_lock.sql
-- P4 (#580)
--
-- 만료 약관 열람 이력 정리 배치(TERMS_VIEW_HISTORY_CLEANUP)가
-- 여러 인스턴스에서 중복 실행되는 것을 막는 락 행 추가
-- ====================================================================
INSERT INTO batch_execution_lock (job_name, currently_running, updated_at)
VALUES ('TERMS_VIEW_HISTORY_CLEANUP', FALSE, NOW());
