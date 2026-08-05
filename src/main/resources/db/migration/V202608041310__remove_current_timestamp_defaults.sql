-- ====================================================================
-- V202608041310__remove_current_timestamp_defaults.sql
-- JPA Auditing 정책 준수를 위해 DB 레벨의 시간 자동 주입(CURRENT_TIMESTAMP) 일괄 제거
-- ====================================================================

ALTER TABLE customer
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

ALTER TABLE terms
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

ALTER TABLE verification_request
    MODIFY COLUMN created_at DATETIME(6) NOT NULL;

ALTER TABLE product
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

ALTER TABLE transaction_sequence
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

ALTER TABLE transfer
    MODIFY COLUMN created_at DATETIME(6) NOT NULL;

ALTER TABLE transfer_limit
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

ALTER TABLE transfer_limit_daily_usage
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

ALTER TABLE auto_transfer
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

ALTER TABLE idempotency_key
    MODIFY COLUMN created_at DATETIME(6) NOT NULL;
