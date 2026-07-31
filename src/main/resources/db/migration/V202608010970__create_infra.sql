-- ====================================================================
-- V202608010970__create_infra.sql
-- 멱등키·감사로그 (P5)

-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
-- ====================================================================

CREATE TABLE idempotency_key (
    idempotency_key   CHAR(36)     NOT NULL COMMENT 'UUID v4',
    customer_id       BIGINT       NOT NULL,
    endpoint          VARCHAR(120) NOT NULL,
    request_hash      CHAR(64)     NOT NULL COMMENT 'SHA-256. *AuthToken 필드 제외 후 계산',
    state             VARCHAR(12)  NOT NULL COMMENT 'PROCESSING / COMPLETED',
    http_status       SMALLINT     NULL,
    response_snapshot JSON         NULL,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at        DATETIME(6)  NOT NULL COMMENT '24시간 후 배치 삭제',
    PRIMARY KEY (idempotency_key),
    KEY ix_idem_expires (expires_at),
    CONSTRAINT fk_idem_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id)
) ENGINE=InnoDB COMMENT='멱등키 (common_rev 7-2)';

CREATE TABLE audit_log (
    audit_log_id       BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id        BIGINT      NULL,
    transaction_number CHAR(20)    NULL,
    event_type         VARCHAR(40) NOT NULL,
    request_ip         VARCHAR(45) NOT NULL,
    result             VARCHAR(12) NOT NULL COMMENT 'SUCCESS / FAILURE',
    detail             JSON        NULL COMMENT '민감정보 마스킹 후 저장',
    requested_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (audit_log_id),
    KEY ix_audit_customer (customer_id, requested_at DESC),
    KEY ix_audit_txno (transaction_number)
) ENGINE=InnoDB COMMENT='감사 로그 (REQ-NFR-010). 조회 API 없으나 규제 근거로 유지';
