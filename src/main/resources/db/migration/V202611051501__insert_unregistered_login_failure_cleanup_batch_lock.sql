-- ====================================================================
-- V202611051501__insert_unregistered_login_failure_cleanup_batch_lock.sql
-- P1 (PH-08 / #900)
--
-- 만료된 미가입 아이디 로그인 실패 정리 배치(UNREGISTERED_LOGIN_FAILURE_CLEANUP)가
-- 여러 인스턴스에서 중복 실행되는 것을 막는 락 행 추가
-- ====================================================================
INSERT INTO batch_execution_lock (job_name, currently_running, updated_at)
VALUES ('UNREGISTERED_LOGIN_FAILURE_CLEANUP', FALSE, NOW());
