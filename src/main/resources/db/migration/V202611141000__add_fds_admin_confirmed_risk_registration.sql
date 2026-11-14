-- 관리자 사기 확정 조치와 위험 계좌 등록 의도·선정 근거를 내구화한다.
ALTER TABLE fds_admin_action
    DROP CHECK ck_fds_action_type,
    ADD CONSTRAINT ck_fds_action_type
        CHECK (action_type IN ('APPROVE','REJECT','FRAUD_REJECT'));

ALTER TABLE fds_risk_account
    ADD COLUMN admin_account_key VARCHAR(64)
        GENERATED ALWAYS AS (
            IF(source_type = 'ADMIN_CONFIRMED', CONCAT(bank_code, ':', account_number), NULL)
        ) STORED,
    ADD UNIQUE KEY uk_fds_risk_admin_account (admin_account_key);

CREATE TABLE fds_risk_registration (
    registration_id CHAR(36) NOT NULL PRIMARY KEY,
    action_id CHAR(36) NOT NULL,
    transfer_hold_id BIGINT NOT NULL,
    investigation_id CHAR(36) NULL,
    actor_id BIGINT NOT NULL,
    recipient_bank_code CHAR(3) NOT NULL,
    recipient_account_number VARCHAR(30) NOT NULL,
    recipient_name VARCHAR(100) NULL,
    call_record_id BIGINT NOT NULL,
    reason_code VARCHAR(40) NOT NULL,
    policy_code VARCHAR(10) NULL,
    policy_count_10m INT NULL,
    policy_amount_10m BIGINT NULL,
    policy_hesitation_in_window BOOLEAN NULL,
    policy_new_recipient_at_window_start BOOLEAN NULL,
    report_investigation_id CHAR(36) NULL,
    score_status VARCHAR(20) NULL,
    behavior_score DECIMAL(8,6) NULL,
    model_version VARCHAR(100) NULL,
    feature_version VARCHAR(100) NULL,
    registration_status VARCHAR(12) NOT NULL,
    failure_reason VARCHAR(80) NULL,
    decision_dataset_version VARCHAR(50) NULL,
    dataset_version VARCHAR(50) NULL,
    risk_account_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    activated_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_fds_risk_registration_action (action_id),
    UNIQUE KEY uk_fds_risk_registration_hold_action (transfer_hold_id, action_id),
    KEY ix_fds_risk_registration_status (registration_status, created_at),
    CONSTRAINT fk_fds_registration_action FOREIGN KEY (action_id)
        REFERENCES fds_admin_action (action_id),
    CONSTRAINT fk_fds_registration_call FOREIGN KEY (call_record_id)
        REFERENCES fds_admin_call_record (call_record_id),
    CONSTRAINT fk_fds_registration_job FOREIGN KEY (investigation_id)
        REFERENCES fds_investigation_job (investigation_id),
    CONSTRAINT fk_fds_registration_risk FOREIGN KEY (risk_account_id)
        REFERENCES fds_risk_account (risk_account_id),
    CONSTRAINT ck_fds_registration_status
        CHECK (registration_status IN ('PENDING','ACTIVE','FAILED'))
) ENGINE=InnoDB;

CREATE TABLE fds_risk_account_evidence (
    evidence_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    risk_account_id BIGINT NOT NULL,
    registration_id CHAR(36) NOT NULL,
    action_id CHAR(36) NOT NULL,
    transfer_hold_id BIGINT NOT NULL,
    investigation_id CHAR(36) NULL,
    actor_id BIGINT NOT NULL,
    call_record_id BIGINT NOT NULL,
    reason_code VARCHAR(40) NOT NULL,
    policy_code VARCHAR(10) NULL,
    agent_report_json JSON NULL,
    evidence_transfer_ids_json JSON NULL,
    score_status VARCHAR(20) NULL,
    behavior_score DECIMAL(8,6) NULL,
    model_version VARCHAR(100) NULL,
    feature_version VARCHAR(100) NULL,
    dataset_version VARCHAR(50) NOT NULL,
    registered_at DATETIME(6) NOT NULL,
    activated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_fds_risk_evidence_registration (registration_id),
    UNIQUE KEY uk_fds_risk_evidence_action (action_id),
    KEY ix_fds_risk_evidence_account_time (risk_account_id, activated_at),
    CONSTRAINT fk_fds_evidence_risk FOREIGN KEY (risk_account_id)
        REFERENCES fds_risk_account (risk_account_id),
    CONSTRAINT fk_fds_evidence_registration FOREIGN KEY (registration_id)
        REFERENCES fds_risk_registration (registration_id),
    CONSTRAINT fk_fds_evidence_action FOREIGN KEY (action_id)
        REFERENCES fds_admin_action (action_id),
    CONSTRAINT fk_fds_evidence_call FOREIGN KEY (call_record_id)
        REFERENCES fds_admin_call_record (call_record_id)
) ENGINE=InnoDB;
