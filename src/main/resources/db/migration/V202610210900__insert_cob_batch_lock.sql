-- ====================================================================
-- V202610210900__insert_cob_batch_lock.sql
-- P4 (#637)
--
-- COB 러너(CobRunnerService)가 여러 인스턴스에서 중복 실행되는 것을 막는 락 행 추가
-- #557에서 러너가 이 락을 쓰기 시작했지만 행을 넣는 마이그레이션이 빠져 있었다
-- ====================================================================
INSERT INTO batch_execution_lock (job_name, currently_running, updated_at)
VALUES ('COB', FALSE, NOW());
