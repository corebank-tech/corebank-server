-- ====================================================================
-- V202610301020__add_audit_log_requested_at_index.sql
-- 대상 테이블: audit_log (소유 P5 · 작업 P1)
--
-- WORM 적재 · 검증 배치는 고객 조건 없이 requested_at 하루 범위로 읽는다(PH-82, #788 리뷰).
-- 기존 인덱스는 customer_id가 앞이라 이 범위 조회에 쓰이지 않는다.
-- ====================================================================
ALTER TABLE audit_log
    ADD INDEX ix_audit_requested_at (requested_at);
