-- ====================================================================
-- V202610271400__add_audit_log_access_reason.sql
-- 대상 테이블: audit_log (소유 P5 · 작업 P1)
--
-- 감사 로그에 관리자 조회·변경 사유를 추가한다(PH-89, #760).
--
-- 관리자가 고객 개인정보를 여는 API는 X-Access-Reason 헤더(D-10)로 사유를 받는다.
-- 그 사유를 감사 5W의 Why로 남긴다. 사유가 없는 기존 감사 행은 NULL이다.
-- ====================================================================

ALTER TABLE audit_log
    ADD COLUMN access_reason VARCHAR(20) NULL COMMENT '관리자 개인정보 조회·변경 사유(PH-89). 그 밖의 행은 NULL'
        AFTER detail;
