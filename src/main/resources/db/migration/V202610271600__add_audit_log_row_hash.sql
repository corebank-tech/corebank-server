-- ====================================================================
-- V202610271600__add_audit_log_row_hash.sql
-- 대상 테이블: audit_log (소유 P5 · 작업 P1), batch_execution_lock
--
-- 감사 로그 행마다 무결성 해시를 남기고, 전날분 WORM 적재 배치의 중복 실행 락을 추가한다(PH-82, #766).
-- row_hash는 id를 뺀 전 컬럼의 키 정렬 JSON SHA-256(64자 hex)이다. 도입 전 행은 NULL이라 적재·검증에서 빠진다.
-- ====================================================================

ALTER TABLE audit_log
    ADD COLUMN row_hash CHAR(64) NULL COMMENT '무결성 해시(PH-82). id 제외 전 컬럼의 SHA-256. 도입 전 행은 NULL'
        AFTER access_reason;

INSERT INTO batch_execution_lock (job_name, currently_running, updated_at)
VALUES ('AUDIT_LOG_ARCHIVE', FALSE, NOW());
