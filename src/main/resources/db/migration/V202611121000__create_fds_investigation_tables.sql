-- P6 Agent 조사 작업·근거·보고서. 점수 처리와 독립적으로 내구화한다.
CREATE TABLE fds_investigation_job (
    transfer_hold_id BIGINT NOT NULL PRIMARY KEY,
    investigation_id CHAR(36) NOT NULL,
    job_status VARCHAR(12) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    lease_expires_at DATETIME(6) NULL,
    last_failure_reason VARCHAR(40) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_fds_investigation_id (investigation_id),
    KEY ix_fds_investigation_due (job_status, next_attempt_at, lease_expires_at),
    CONSTRAINT ck_fds_investigation_status CHECK (job_status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_fds_investigation_attempt CHECK (attempt_count BETWEEN 0 AND 3)
) ENGINE=InnoDB;

CREATE TABLE fds_investigation_evidence (
    investigation_id CHAR(36) NOT NULL,
    ref_id BIGINT NOT NULL,
    transfer_hold_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    range_from DATETIME(6) NOT NULL,
    range_to DATETIME(6) NOT NULL,
    provided_at DATETIME(6) NOT NULL,
    PRIMARY KEY (investigation_id, ref_id),
    CONSTRAINT fk_fds_evidence_job FOREIGN KEY (investigation_id)
        REFERENCES fds_investigation_job (investigation_id)
) ENGINE=InnoDB;

CREATE TABLE fds_investigation_report (
    investigation_id CHAR(36) NOT NULL PRIMARY KEY,
    transfer_hold_id BIGINT NOT NULL,
    schema_version VARCHAR(30) NOT NULL,
    report_json MEDIUMTEXT NOT NULL,
    report_sha256 CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_fds_report_hold UNIQUE (transfer_hold_id),
    CONSTRAINT fk_fds_report_job FOREIGN KEY (investigation_id)
        REFERENCES fds_investigation_job (investigation_id)
) ENGINE=InnoDB;
