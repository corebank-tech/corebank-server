-- ====================================================================
-- V202611060600__recreate_objection_decision_hold_check.sql
-- 대상 테이블: objection_decision (제약 변경 · P3)
--
-- V202611060200 의 ck_objection_decision_hold 는 확정된 PARTIAL 인데 보류액이 NULL 이면 통과했다.
-- 첫 조건의 비교가 UNKNOWN, 둘째 조건이 FALSE 가 되고 MySQL CHECK 는 UNKNOWN 을 통과로 보기 때문이다.
-- 확정된 PARTIAL 은 두 보류액이 모두 있어야 한다고 IS NOT NULL 로 적어 다시 만든다. 뜻은 그대로다.
-- 이미 적용된 V202611060200 은 체크섬 때문에 고치지 않는다. 지금까지는 도메인(ObjectionDecision)이 막아 위반 행이 없다.
-- ====================================================================

ALTER TABLE objection_decision DROP CHECK ck_objection_decision_hold;

ALTER TABLE objection_decision
    ADD CONSTRAINT ck_objection_decision_hold CHECK (
        (result = 'PARTIAL' AND status = 'CONFIRMED'
            AND requested_hold_amount IS NOT NULL AND applied_hold_amount IS NOT NULL
            AND requested_hold_amount > 0
            AND applied_hold_amount >= 0 AND applied_hold_amount <= requested_hold_amount)
        OR ((result <> 'PARTIAL' OR status <> 'CONFIRMED')
            AND requested_hold_amount IS NULL AND applied_hold_amount IS NULL)
    );
