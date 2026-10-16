-- ====================================================================
-- V202610091901__insert_auth_token_cleanup_batch_lock.sql
-- P4 (#580)
--
-- 만료 인증 토큰 정리 배치(AUTH_TOKEN_CLEANUP)가
-- 여러 인스턴스에서 중복 실행되는 것을 막는 락 행 추가
-- ====================================================================
INSERT INTO batch_execution_lock (job_name, currently_running, updated_at)
VALUES ('AUTH_TOKEN_CLEANUP', FALSE, NOW());
