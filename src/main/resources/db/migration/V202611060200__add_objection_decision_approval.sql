-- ====================================================================
-- V202611060200__add_objection_decision_approval.sql
-- 대상 테이블: objection_decision (컬럼 추가 · 제약 변경 · P3)
--
-- 이의제기 AI 심사 지원(D-36) PA-10 2인 승인(Plan D13). 보류를 줄이는 판정(수용, 일부 지급정지)은 작성자와 다른
-- 승인자가 승인해야 확정되고, 불수용은 작성 즉시 확정된다. 승인자가 반려하면 판정 행은 RETURNED 로 남고 다시 작성한다.
-- 그래서 이의제기당 판정 행이 여럿일 수 있다. 반려되지 않은 판정(승인 대기 또는 확정)은 이의제기당 하나뿐이라
-- 생성 열 active_objection_id 의 유니크 키로 막는다.
-- 일부 지급정지 보류액은 승인 때 계좌에 반영하면서 정해지므로 확정된 PARTIAL 만 갖는다.
-- ====================================================================

-- FK 가 쓰던 유니크 인덱스를 지우기 전에 같은 열의 일반 인덱스를 먼저 만든다
ALTER TABLE objection_decision
    ADD KEY ix_objection_decision_objection (objection_id, decision_id);

ALTER TABLE objection_decision
    DROP INDEX uk_objection_decision_objection,
    DROP CHECK ck_objection_decision_hold;

ALTER TABLE objection_decision
    MODIFY COLUMN decided_by BIGINT NOT NULL COMMENT '판정을 작성한 관리자 customer_id',
    MODIFY COLUMN decided_at DATETIME(6) NOT NULL COMMENT '판정 작성 시각. 불수용은 확정 시각과 같다',
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED'
        COMMENT 'PENDING_APPROVAL 승인 대기 / CONFIRMED 확정 / RETURNED 반려' AFTER applied_hold_amount,
    ADD COLUMN approved_by BIGINT NULL COMMENT '승인한 관리자 customer_id. 작성자와 달라야 한다. 불수용은 NULL' AFTER decided_at,
    ADD COLUMN approved_at DATETIME(6) NULL COMMENT '승인 시각. 계좌 반영 시각과 같다' AFTER approved_by,
    ADD COLUMN returned_by BIGINT NULL COMMENT '반려한 관리자 customer_id' AFTER approved_at,
    ADD COLUMN returned_at DATETIME(6) NULL AFTER returned_by,
    ADD COLUMN return_reason VARCHAR(500) NULL COMMENT '반려 사유' AFTER returned_at,
    ADD COLUMN active_objection_id BIGINT
        GENERATED ALWAYS AS (IF(status = 'RETURNED', NULL, objection_id)) STORED
        COMMENT '반려되지 않은 판정만 objection_id. 유니크 키로 이의제기당 하나만 둔다' AFTER return_reason;

ALTER TABLE objection_decision ALTER COLUMN status DROP DEFAULT;

ALTER TABLE objection_decision
    ADD UNIQUE KEY uk_objection_decision_active (active_objection_id),
    ADD CONSTRAINT ck_objection_decision_status CHECK (status IN ('PENDING_APPROVAL', 'CONFIRMED', 'RETURNED')),
    ADD CONSTRAINT ck_objection_decision_hold CHECK (
        (result = 'PARTIAL' AND status = 'CONFIRMED' AND requested_hold_amount > 0
            AND applied_hold_amount >= 0 AND applied_hold_amount <= requested_hold_amount)
        OR ((result <> 'PARTIAL' OR status <> 'CONFIRMED')
            AND requested_hold_amount IS NULL AND applied_hold_amount IS NULL)
    ),
    -- 불수용은 승인 단계가 없다. 수용과 일부 지급정지는 승인 대기에서 시작한다
    ADD CONSTRAINT ck_objection_decision_reject_single CHECK (result <> 'REJECTED' OR status = 'CONFIRMED'),
    ADD CONSTRAINT ck_objection_decision_approval CHECK (
        ((result <> 'REJECTED' AND status = 'CONFIRMED') = (approved_by IS NOT NULL AND approved_at IS NOT NULL))
        AND (approved_by IS NULL OR approved_by <> decided_by)
    ),
    ADD CONSTRAINT ck_objection_decision_return CHECK (
        (status = 'RETURNED') = (returned_by IS NOT NULL AND returned_at IS NOT NULL AND return_reason IS NOT NULL)
    );
