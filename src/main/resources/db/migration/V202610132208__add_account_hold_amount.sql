ALTER TABLE account
    ADD COLUMN hold_amount BIGINT NOT NULL DEFAULT 0 AFTER balance,
    ADD CONSTRAINT ck_account_hold_amount
        CHECK (hold_amount >= 0 AND hold_amount <= balance);