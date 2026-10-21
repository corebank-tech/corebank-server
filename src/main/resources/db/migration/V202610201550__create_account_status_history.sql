-- ====================================================================
-- V202610201550__create_account_status_history.sql
-- PH-15 계좌 상태 전이 감사 이력 (#671)
--
-- 계좌 상태가 실제로 변경된 경우에만 이력을 추가한다.
-- 이벤트 전달 기록과 별도로 영속 보관하며, UPDATE·DELETE하지 않는다.
-- account에 FK를 걸지 않아 계좌 정리와 관계없이 이력을 보존한다.
-- ====================================================================

CREATE TABLE account_status_history (
    history_id              BIGINT      NOT NULL AUTO_INCREMENT,
    account_id              BIGINT      NOT NULL,
    previous_status         VARCHAR(12) NOT NULL,
    new_status              VARCHAR(12) NOT NULL,
    maturity_date           DATE        NULL,
    effective_business_date DATE        NULL,
    changed_at              DATETIME(6) NOT NULL,
    trigger_type            VARCHAR(20) NOT NULL,

    PRIMARY KEY (history_id),
    KEY ix_account_status_history_account_time (account_id, changed_at)
) ENGINE=InnoDB COMMENT='계좌 상태 전이 감사 이력 (PH-15)';
