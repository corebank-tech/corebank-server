-- ====================================================================
-- V202611091500__create_payment_stop_tables.sql
-- 대상 테이블: payment_stop_case, case_disputed_transfer, objection, objection_decision, gate_evaluation (신규 · P3)
--
-- 이의제기 AI 심사 지원(D-36)의 paymentstop 도메인 뼈대(PA-04).
-- 계좌 상태와 보류액은 account 도메인의 account_payment_stop 이 맡고, 여기서는 사건과 심사 기록만 둔다.
-- account 에 FK 를 걸지 않는다. 다른 도메인은 account.api 로만 다룬다(AGENTS 규칙 2).
-- ====================================================================

CREATE TABLE payment_stop_case (
    case_id            BIGINT      NOT NULL AUTO_INCREMENT,
    account_id         BIGINT      NOT NULL COMMENT '지급정지 계좌. account.api 로만 다룬다',
    sender_account_id  BIGINT      NULL COMMENT '다툼 거래 송금인의 당행 계좌. 타행이거나 송금인이 여럿이면 NULL',
    objection_deadline DATE        NOT NULL COMMENT '이의제기 신청 기한. 담당자가 등록 때 넣는다',
    status             VARCHAR(12) NOT NULL COMMENT 'STOPPED 전부 지급정지 / PARTIAL 일부 지급정지 / RELEASED 해제',
    registered_by      BIGINT      NOT NULL COMMENT '등록한 관리자 customer_id',
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,

    PRIMARY KEY (case_id),
    KEY ix_payment_stop_case_account (account_id),
    CONSTRAINT ck_payment_stop_case_status CHECK (status IN ('STOPPED', 'PARTIAL', 'RELEASED'))
) ENGINE=InnoDB COMMENT='지급정지 사건 (D-36)';

CREATE TABLE case_disputed_transfer (
    disputed_transfer_id BIGINT      NOT NULL AUTO_INCREMENT,
    case_id              BIGINT      NOT NULL,
    transaction_number   CHAR(20)    NOT NULL COMMENT '피해 신고된 입금 거래번호',
    amount               BIGINT      NOT NULL COMMENT '등록 시점 원장 금액',
    occurred_at          DATETIME(6) NOT NULL COMMENT '등록 시점 원장 시각',

    PRIMARY KEY (disputed_transfer_id),
    UNIQUE KEY uk_case_disputed_transfer_txno (transaction_number),
    KEY ix_case_disputed_transfer_case (case_id),
    CONSTRAINT fk_case_disputed_transfer_case FOREIGN KEY (case_id) REFERENCES payment_stop_case (case_id),
    CONSTRAINT ck_case_disputed_transfer_amount CHECK (amount > 0)
) ENGINE=InnoDB COMMENT='지급정지 사건의 다툼 거래 (D-36)';

CREATE TABLE objection (
    objection_id         BIGINT      NOT NULL AUTO_INCREMENT,
    case_id              BIGINT      NOT NULL,
    clause               TINYINT     NOT NULL COMMENT '사유 조항. 법 제7조제1항 1~3호. 해제 범위를 정한다',
    statement            TEXT        NOT NULL COMMENT '명의인 소명 원문. 상세 조회는 조회 사유와 감사를 남긴다',
    simplified_requested BOOLEAN     NOT NULL COMMENT '간소화(일부 지급정지) 절차 신청',
    id_copy_confirmed    BOOLEAN     NOT NULL COMMENT '신분증 사본 확인(시행령 제7조)',
    litigation_pending   BOOLEAN     NOT NULL COMMENT '명의인과 피해자 사이 소송 계속',
    status               VARCHAR(12) NOT NULL COMMENT 'RECEIVED 접수 / DECIDED 판정 확정',
    received_by          BIGINT      NOT NULL COMMENT '접수한 관리자 customer_id',
    received_at          DATETIME(6) NOT NULL,
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6) NOT NULL,

    PRIMARY KEY (objection_id),
    UNIQUE KEY uk_objection_case (case_id),
    CONSTRAINT fk_objection_case FOREIGN KEY (case_id) REFERENCES payment_stop_case (case_id),
    CONSTRAINT ck_objection_clause CHECK (clause BETWEEN 1 AND 3),
    CONSTRAINT ck_objection_status CHECK (status IN ('RECEIVED', 'DECIDED'))
) ENGINE=InnoDB COMMENT='지급정지 이의제기 (D-36)';

CREATE TABLE objection_decision (
    decision_id           BIGINT       NOT NULL AUTO_INCREMENT,
    objection_id          BIGINT       NOT NULL,
    result                VARCHAR(10)  NOT NULL COMMENT 'ACCEPTED 수용 / PARTIAL 일부 지급정지 / REJECTED 불수용',
    reason                VARCHAR(500) NOT NULL,
    early_release_basis   VARCHAR(500) NULL COMMENT '조기 해제 근거(근거 거래 ID나 외부 확인). 수용과 일부 지급정지는 필수',
    requested_hold_amount BIGINT       NULL COMMENT '일부 지급정지 요청 보류액. PARTIAL 만',
    applied_hold_amount   BIGINT       NULL COMMENT '실제로 묶은 보류액. PARTIAL 만',
    decided_by            BIGINT       NOT NULL COMMENT '확정한 관리자 customer_id',
    decided_at            DATETIME(6)  NOT NULL,

    PRIMARY KEY (decision_id),
    UNIQUE KEY uk_objection_decision_objection (objection_id),
    CONSTRAINT fk_objection_decision_objection FOREIGN KEY (objection_id) REFERENCES objection (objection_id),
    CONSTRAINT ck_objection_decision_result CHECK (result IN ('ACCEPTED', 'PARTIAL', 'REJECTED')),
    CONSTRAINT ck_objection_decision_basis CHECK (result = 'REJECTED' OR early_release_basis IS NOT NULL),
    CONSTRAINT ck_objection_decision_hold CHECK (
        (result = 'PARTIAL' AND requested_hold_amount > 0
            AND applied_hold_amount >= 0 AND applied_hold_amount <= requested_hold_amount)
        OR (result <> 'PARTIAL' AND requested_hold_amount IS NULL AND applied_hold_amount IS NULL)
    )
) ENGINE=InnoDB COMMENT='이의제기 판정 (D-36)';

CREATE TABLE gate_evaluation (
    gate_evaluation_id  BIGINT      NOT NULL AUTO_INCREMENT,
    objection_id        BIGINT      NOT NULL,
    purpose             VARCHAR(12) NOT NULL COMMENT 'DECISION 판정 확정 직전 계산. Agent 분석 시점 계산(ANALYSIS)은 PA-05',
    branch              VARCHAR(16) NOT NULL COMMENT 'FRAUD_PATTERN / NORMAL_ACCEPT / NORMAL_PARTIAL / UNDETERMINED',
    simplified_excluded BOOLEAN     NOT NULL,
    rules_version       VARCHAR(20) NOT NULL,
    settings            JSON        NOT NULL COMMENT '계산에 쓴 게이트 설정값. 같은 판정을 재현하려고 남긴다(D10)',
    rule_results        JSON        NOT NULL COMMENT '규칙별 결과와 근거 거래번호',
    evaluated_at        DATETIME(6) NOT NULL,

    PRIMARY KEY (gate_evaluation_id),
    KEY ix_gate_evaluation_objection (objection_id, evaluated_at),
    CONSTRAINT fk_gate_evaluation_objection FOREIGN KEY (objection_id) REFERENCES objection (objection_id),
    CONSTRAINT ck_gate_evaluation_purpose CHECK (purpose IN ('DECISION', 'ANALYSIS')),
    CONSTRAINT ck_gate_evaluation_branch
        CHECK (branch IN ('FRAUD_PATTERN', 'NORMAL_ACCEPT', 'NORMAL_PARTIAL', 'UNDETERMINED'))
) ENGINE=InnoDB COMMENT='해제 게이트 계산 기록 (D-36)';
