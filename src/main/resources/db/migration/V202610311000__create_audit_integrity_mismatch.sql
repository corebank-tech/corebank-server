-- ====================================================================
-- V202610311000__create_audit_integrity_mismatch.sql
-- 대상 테이블: audit_integrity_mismatch (신규 · P1)
--
-- 검증 배치가 WORM 사본과 DB 감사 행을 대조해 찾은 불일치를 날짜별로 남긴다(PH-82, #766).
-- 증거라 지우지 않는다. 같은 날을 다시 검증해도 이미 남긴 불일치는 건너뛰고 새로 찾은 것만 쌓는다.
-- audit_log에 FK를 걸지 않는다 — 지워진 행도 기록해야 한다.
-- ====================================================================

CREATE TABLE audit_integrity_mismatch (
    audit_integrity_mismatch_id BIGINT      NOT NULL AUTO_INCREMENT,
    archive_date                DATE        NOT NULL COMMENT '검증한 사본의 날짜 (audit_log.requested_at 기준)',
    audit_log_id                BIGINT      NOT NULL,
    mismatch_type               VARCHAR(20) NOT NULL COMMENT 'TAMPERED / MISSING_IN_DB / NOT_ARCHIVED',
    detected_at                 DATETIME(6) NOT NULL COMMENT '검증 배치가 불일치를 확인한 시각',
    PRIMARY KEY (audit_integrity_mismatch_id),
    UNIQUE KEY uk_audit_integrity_mismatch_date_log_type (archive_date, audit_log_id, mismatch_type)
) ENGINE=InnoDB COMMENT='감사 로그 무결성 불일치 목록 (PH-82, #766)';
