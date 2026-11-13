ALTER TABLE transfer_hold
    ADD COLUMN policy_count_10m INT NULL AFTER policy_code,
    ADD COLUMN policy_amount_10m BIGINT NULL AFTER policy_count_10m,
    ADD COLUMN policy_hesitation_in_window BOOLEAN NULL AFTER policy_amount_10m,
    ADD COLUMN policy_new_recipient_at_window_start BOOLEAN NULL AFTER policy_hesitation_in_window;
