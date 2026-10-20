-- ====================================================================
-- V202610201223__alter_ledger_transaction_type_comment_interbank.sql
-- 타행이체 기표 유형 반영 (P4, PH-33-② / #654)
--
-- transaction_type은 CHECK 없이 주석으로만 값을 관리하므로 주석만 바꾼다.
-- INTERBANK_TRANSFER는 출금 1행이고, 실패로 확정되면 같은 거래번호로 되돌림 입금 1행(reversal_id)이 붙는다.
-- ====================================================================

-- 타입·NULL을 그대로 둬야 메타데이터만 바뀐다. rebuild가 필요해지면 조용히 돌지 말고 실패하도록 알고리즘을 못박는다
ALTER TABLE ledger_entry
    MODIFY COLUMN transaction_type VARCHAR(32) NOT NULL COMMENT 'OPENING / IMMEDIATE_TRANSFER / SCHEDULED_TRANSFER / AUTO_TRANSFER / PRODUCT_SUBSCRIPTION / INTEREST / MATURITY / TERMINATION / REVERSAL / INTERBANK_TRANSFER',
    ALGORITHM = INPLACE, LOCK = NONE;
