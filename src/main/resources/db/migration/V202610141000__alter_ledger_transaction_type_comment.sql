-- ====================================================================
-- V202610141000__alter_ledger_transaction_type_comment.sql
-- 예적금 만기해지(MATURITY)·중도해지(TERMINATION) 기표 유형 반영 (P4, PH-99 입금 계약 / #595)
--
-- transaction_type은 CHECK 없이 주석으로만 값을 관리하므로 주석만 바꾼다. 빠져 있던 OPENING도 함께 적는다.
-- ====================================================================

-- 타입·NULL을 그대로 둬야 메타데이터만 바뀐다. rebuild가 필요해지면 조용히 돌지 말고 실패하도록 알고리즘을 못박는다
ALTER TABLE ledger_entry
    MODIFY COLUMN transaction_type VARCHAR(32) NOT NULL COMMENT 'OPENING / IMMEDIATE_TRANSFER / SCHEDULED_TRANSFER / AUTO_TRANSFER / PRODUCT_SUBSCRIPTION / INTEREST / MATURITY / TERMINATION / REVERSAL',
    ALGORITHM = INPLACE, LOCK = NONE;
