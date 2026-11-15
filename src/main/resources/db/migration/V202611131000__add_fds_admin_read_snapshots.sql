-- 조사 당시 Agent에게 제공한 Tool 응답을 관리자 상세에서 재현한다.
CREATE TABLE fds_investigation_history_snapshot (
    investigation_id CHAR(36) NOT NULL PRIMARY KEY,
    transfer_hold_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    range_from DATETIME(6) NOT NULL,
    range_to DATETIME(6) NOT NULL,
    has_more BOOLEAN NOT NULL,
    provided_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_fds_history_snapshot_job FOREIGN KEY (investigation_id)
        REFERENCES fds_investigation_job (investigation_id)
) ENGINE=InnoDB;

CREATE TABLE fds_investigation_history_item (
    investigation_id CHAR(36) NOT NULL,
    ref_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    transferred_at DATETIME(6) NOT NULL,
    recipient_account_masked VARCHAR(40) NOT NULL,
    PRIMARY KEY (investigation_id, ref_id),
    CONSTRAINT fk_fds_history_item_snapshot FOREIGN KEY (investigation_id)
        REFERENCES fds_investigation_history_snapshot (investigation_id)
) ENGINE=InnoDB;

CREATE TABLE fds_investigation_recipient_snapshot (
    investigation_id CHAR(36) NOT NULL PRIMARY KEY,
    transfer_hold_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    first_succeeded_at DATETIME(6) NULL,
    succeeded_before_window BOOLEAN NOT NULL,
    other_withdrawal_account_count INT NOT NULL,
    provided_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_fds_recipient_snapshot_job FOREIGN KEY (investigation_id)
        REFERENCES fds_investigation_job (investigation_id)
) ENGINE=InnoDB;
