-- ====================================================================
-- V202611091735__add_objection_decision_discretionary.sql
-- 대상 테이블: objection_decision (컬럼 추가 · 제약 추가 · P3)
--
-- 담당자 재량 판정(ADR-0011). 해제 게이트가 닫은 수용과 일부 지급정지를 담당자가 사유를 남기고 쓸 수 있게 한다.
-- 재량 판정은 사유가 필수라 CHECK 로 막는다. 작성 시점 게이트 갈래는 재량 여부와 무관하게 새 판정마다 남긴다.
-- 이미 있는 판정은 재량이 아니고(DEFAULT FALSE) 작성 시점 갈래를 모르므로 NULL 로 둔다.
-- ====================================================================

ALTER TABLE objection_decision
    ADD COLUMN discretionary BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '담당자 재량 판정인지. 게이트가 닫은 수용, 일부 지급정지를 사유와 2인 승인으로 쓴 판정' AFTER return_reason,
    ADD COLUMN discretionary_reason VARCHAR(1000) NULL
        COMMENT '재량 사유(20~1000자는 애플리케이션이 검사). 재량 판정만' AFTER discretionary,
    ADD COLUMN gate_branch_at_write VARCHAR(24) NULL
        COMMENT '작성 시점 게이트 갈래. 이 컬럼 추가 전 판정은 NULL' AFTER discretionary_reason;

ALTER TABLE objection_decision
    ADD CONSTRAINT ck_objection_decision_discretionary CHECK (discretionary = FALSE OR discretionary_reason IS NOT NULL);
