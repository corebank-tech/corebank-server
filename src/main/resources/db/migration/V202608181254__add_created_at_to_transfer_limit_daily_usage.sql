-- ====================================================================
-- V202608181254__add_created_at_to_transfer_limit_daily_usage.sql
-- 이체한도 (P1)
--
-- V202608181253 과 같은 이유로 transfer_limit_daily_usage 에도
-- created_at 을 추가한다. 테이블별로 파일을 나눈 근거는 그쪽 주석에 있다.
-- ====================================================================

ALTER TABLE transfer_limit_daily_usage
    ADD COLUMN created_at DATETIME(6) NULL AFTER used_amount;

UPDATE transfer_limit_daily_usage
SET created_at = updated_at
WHERE created_at IS NULL;

ALTER TABLE transfer_limit_daily_usage
    MODIFY COLUMN created_at DATETIME(6) NOT NULL;
