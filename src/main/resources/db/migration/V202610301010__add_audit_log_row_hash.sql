-- ====================================================================
-- V202610301010__add_audit_log_row_hash.sql
-- 대상 테이블: audit_log (소유 P5 · 작업 P1)
--
-- 감사 로그 행마다 무결성 해시를 남긴다(PH-82, #766).
-- row_hash는 id를 뺀 전 컬럼의 키 정렬 JSON SHA-256(64자 hex)이다. 도입 전 행은 NULL이라 적재 대상이 아니다.
-- ====================================================================

ALTER TABLE audit_log
    ADD COLUMN row_hash CHAR(64) NULL COMMENT '무결성 해시(PH-82). id 제외 전 컬럼의 SHA-256. 도입 전 행은 NULL'
        AFTER access_reason;
