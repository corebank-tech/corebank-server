-- P6 행동 흐름·이벤트와 보류 후 보조 점수. 원장 및 P4 보류 상태와 분리한다.
CREATE TABLE fds_behavior_flow (
    flow_id CHAR(36) NOT NULL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    first_received_at DATETIME(6) NOT NULL,
    last_received_at DATETIME(6) NOT NULL,
    finalized_at DATETIME(6) NULL,
    confirm_active_seconds INT NULL,
    KEY ix_fds_behavior_flow_customer (customer_id, first_received_at),
    CONSTRAINT ck_fds_confirm_active CHECK (confirm_active_seconds IS NULL OR confirm_active_seconds BETWEEN 0 AND 1800)
) ENGINE=InnoDB;

CREATE TABLE fds_behavior_event (
    flow_id CHAR(36) NOT NULL,
    seq_no INT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    step_code VARCHAR(4) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    PRIMARY KEY (flow_id, seq_no),
    CONSTRAINT fk_fds_behavior_event_flow FOREIGN KEY (flow_id) REFERENCES fds_behavior_flow (flow_id),
    CONSTRAINT ck_fds_behavior_seq CHECK (seq_no BETWEEN 1 AND 100)
) ENGINE=InnoDB;

CREATE TABLE fds_behavior_score (
    transfer_hold_id BIGINT NOT NULL PRIMARY KEY,
    flow_id CHAR(36) NULL,
    score_status VARCHAR(12) NOT NULL,
    behavior_risk_score DOUBLE NULL,
    model_version VARCHAR(80) NULL,
    feature_version VARCHAR(80) NULL,
    unavailable_reason VARCHAR(40) NULL,
    score_attempt_started_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY ix_fds_behavior_score_status (score_status, score_attempt_started_at),
    CONSTRAINT ck_fds_behavior_score_range CHECK (behavior_risk_score IS NULL OR behavior_risk_score BETWEEN 0 AND 1)
) ENGINE=InnoDB;
