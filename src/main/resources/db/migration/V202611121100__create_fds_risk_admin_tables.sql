-- P6 위험 데이터셋과 관리자 콜·조치·감사를 내구화한다.
CREATE TABLE fds_risk_dataset (
    dataset_version VARCHAR(50) NOT NULL PRIMARY KEY,
    dataset_status VARCHAR(12) NOT NULL,
    expected_count INT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    activated_at DATETIME(6) NULL,
    CONSTRAINT ck_fds_risk_dataset_status CHECK (dataset_status IN ('LOADED', 'ACTIVE', 'FAILED'))
) ENGINE=InnoDB;

CREATE TABLE fds_risk_account (
    risk_account_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    dataset_version VARCHAR(50) NOT NULL,
    bank_code CHAR(3) NOT NULL,
    account_number VARCHAR(30) NOT NULL,
    source_type VARCHAR(20) NOT NULL DEFAULT 'SEED',
    reason VARCHAR(200) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_fds_risk_account_key (dataset_version, bank_code, account_number),
    CONSTRAINT fk_fds_risk_account_dataset FOREIGN KEY (dataset_version)
        REFERENCES fds_risk_dataset (dataset_version)
) ENGINE=InnoDB;

CREATE TABLE fds_risk_active_version (
    singleton_id TINYINT NOT NULL PRIMARY KEY,
    dataset_version VARCHAR(50) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_fds_risk_active_singleton CHECK (singleton_id = 1),
    CONSTRAINT fk_fds_risk_active_dataset FOREIGN KEY (dataset_version)
        REFERENCES fds_risk_dataset (dataset_version)
) ENGINE=InnoDB;

CREATE TABLE fds_admin_call_record (
    call_record_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transfer_hold_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    call_result VARCHAR(24) NOT NULL,
    admin_id BIGINT NOT NULL,
    investigation_reason VARCHAR(200) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_fds_call_idempotency (idempotency_key),
    KEY ix_fds_call_hold_time (transfer_hold_id, recorded_at),
    CONSTRAINT ck_fds_call_result CHECK (call_result IN ('CUSTOMER_CONFIRMED','CUSTOMER_DENIED','NO_ANSWER','INCONCLUSIVE'))
) ENGINE=InnoDB;

CREATE TABLE fds_admin_action (
    action_id CHAR(36) NOT NULL PRIMARY KEY,
    transfer_hold_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    actor_id BIGINT NOT NULL,
    action_type VARCHAR(12) NOT NULL,
    reason_code VARCHAR(40) NULL,
    call_record_id BIGINT NULL,
    risk_status VARCHAR(12) NOT NULL,
    risk_account_id BIGINT NULL,
    dataset_version VARCHAR(50) NULL,
    unavailable_reason VARCHAR(40) NULL,
    action_status VARCHAR(12) NOT NULL,
    failure_reason VARCHAR(50) NULL,
    p4_status VARCHAR(20) NULL,
    p4_actor_id BIGINT NULL,
    p4_reason_code VARCHAR(40) NULL,
    open_hold_id BIGINT GENERATED ALWAYS AS (IF(action_status = 'PENDING', transfer_hold_id, NULL)) STORED,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_fds_action_idempotency (idempotency_key),
    UNIQUE KEY uk_fds_action_open_hold (open_hold_id),
    CONSTRAINT fk_fds_action_call FOREIGN KEY (call_record_id) REFERENCES fds_admin_call_record (call_record_id),
    CONSTRAINT ck_fds_action_type CHECK (action_type IN ('APPROVE','REJECT')),
    CONSTRAINT ck_fds_action_risk CHECK (risk_status IN ('MATCH','NO_MATCH','UNAVAILABLE')),
    CONSTRAINT ck_fds_action_status CHECK (action_status IN ('PENDING','COMPLETED','FAILED','CONFLICT'))
) ENGINE=InnoDB;

CREATE TABLE fds_admin_action_audit (
    action_id CHAR(36) NOT NULL PRIMARY KEY,
    transfer_hold_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action_type VARCHAR(12) NOT NULL,
    reason_code VARCHAR(40) NULL,
    call_record_id BIGINT NULL,
    risk_status VARCHAR(12) NOT NULL,
    risk_account_id BIGINT NULL,
    dataset_version VARCHAR(50) NULL,
    p4_status VARCHAR(20) NOT NULL,
    p4_actor_id BIGINT NULL,
    p4_reason_code VARCHAR(40) NULL,
    completed_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_fds_audit_action FOREIGN KEY (action_id) REFERENCES fds_admin_action (action_id)
) ENGINE=InnoDB;
