-- ====================================================================
-- V202611041500__create_account_payment_stop.sql
-- 대상 테이블: account_payment_stop (신규 · P3)
--
-- 이의제기 AI 심사 지원(D-36)의 계좌 지급정지 계약(account.api)이 쓰는 사건별 지급정지 기록이다.
-- 계좌의 status와 hold_amount는 이 테이블의 유효한 행으로 계산한다.
--   - 전부 지급정지(FULL)가 하나라도 있으면 SUSPENDED
--   - 일부 지급정지(PARTIAL)만 있으면 정지 전 상태에 applied_amount 합계만큼 보류
--   - 마지막 행이 해제되면 status_before로 돌아간다
-- 해제해도 행을 지우지 않는다. 다른 사건의 지급정지 이력을 보는 데 쓴다.
-- case_id는 paymentstop 도메인의 사건 ID다. 그 테이블이 아직 없어 FK를 걸지 않는다.
-- ====================================================================

CREATE TABLE account_payment_stop (
    payment_stop_id  BIGINT      NOT NULL AUTO_INCREMENT,
    account_id       BIGINT      NOT NULL,
    case_id          BIGINT      NOT NULL,
    stop_type        VARCHAR(10) NOT NULL COMMENT 'FULL 전부 지급정지 / PARTIAL 일부 지급정지',
    requested_amount BIGINT      NOT NULL DEFAULT 0 COMMENT '일부 지급정지 요청 보류액. FULL은 0',
    applied_amount   BIGINT      NOT NULL DEFAULT 0 COMMENT '실제로 묶은 보류액. 잔액에서 다른 사건 보류액을 뺀 금액이 상한. FULL은 0',
    status_before    VARCHAR(12) NOT NULL COMMENT '첫 지급정지를 걸기 전 계좌 상태. 마지막 해제 때 이 상태로 돌아간다',
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       DATETIME(6) NOT NULL,
    released_at      DATETIME(6) NULL,

    PRIMARY KEY (payment_stop_id),
    UNIQUE KEY uk_account_payment_stop_account_case (account_id, case_id),
    CONSTRAINT fk_aps_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT ck_aps_stop_type CHECK (stop_type IN ('FULL', 'PARTIAL')),
    CONSTRAINT ck_aps_status_before CHECK (status_before IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_aps_amount CHECK (requested_amount >= 0 AND applied_amount >= 0 AND applied_amount <= requested_amount),
    CONSTRAINT ck_aps_released CHECK ((active = TRUE AND released_at IS NULL) OR (active = FALSE AND released_at IS NOT NULL))
) ENGINE=InnoDB COMMENT='계좌 사건별 지급정지 기록 (D-36)';
