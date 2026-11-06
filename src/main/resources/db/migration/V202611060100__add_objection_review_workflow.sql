-- ====================================================================
-- V202611060100__add_objection_review_workflow.sql
-- 대상 테이블: objection (컬럼 추가 · 상태 확장), objection_supplement_request, objection_review_session (신규 · P3)
--
-- 이의제기 AI 심사 지원(D-36) PA-10. 소명 완비 확인과 심사 기한, 보완 요청과 보완 기한, 심사 시작 기록.
-- 기한 기산일은 담당자의 소명 완비 확인 시점이고 영업일로 센다(Plan D12). 보완은 2회까지다.
-- 보완 기한이 지나도 자동으로 확정하지 않는다. 목록에 불수용 대상으로 보이고 담당자가 확정한다(D32).
-- ====================================================================

ALTER TABLE objection DROP CHECK ck_objection_status;

ALTER TABLE objection
    MODIFY COLUMN status VARCHAR(20) NOT NULL
        COMMENT 'RECEIVED 접수 / UNDER_REVIEW 심사 중 / SUPPLEMENT_REQUESTED 보완 요청 / SUPPLEMENT_SUBMITTED 보완 제출 / PENDING_APPROVAL 승인 대기 / DECIDED 확정',
    MODIFY COLUMN received_by BIGINT NOT NULL
        COMMENT '접수한 사람의 customer_id. BRANCH 면 관리자, ONLINE 이면 명의인',
    ADD COLUMN received_channel VARCHAR(10) NOT NULL DEFAULT 'BRANCH'
        COMMENT 'BRANCH 영업점 대리 접수 / ONLINE 고객 비대면 접수(D34)' AFTER received_by,
    ADD COLUMN completeness_confirmed_by BIGINT NULL
        COMMENT '마지막으로 소명 완비를 확인한 관리자 customer_id' AFTER received_at,
    ADD COLUMN completeness_confirmed_at DATETIME(6) NULL
        COMMENT '마지막 소명 완비 확인 시각. 심사 기한의 기산점(D12)' AFTER completeness_confirmed_by,
    ADD COLUMN review_deadline DATE NULL
        COMMENT '심사 기한. 완비 확인일 + 5영업일. 보완 요청 중에는 NULL' AFTER completeness_confirmed_at,
    ADD COLUMN supplement_round TINYINT NOT NULL DEFAULT 0
        COMMENT '지금까지 보완 요청 횟수. 2회까지' AFTER review_deadline,
    ADD COLUMN supplement_due DATE NULL
        COMMENT '마지막 보완 요청의 기한. 1회째 + 5영업일, 2회째 + 3영업일' AFTER supplement_round;

ALTER TABLE objection
    ADD CONSTRAINT ck_objection_status CHECK (status IN (
        'RECEIVED', 'UNDER_REVIEW', 'SUPPLEMENT_REQUESTED', 'SUPPLEMENT_SUBMITTED', 'PENDING_APPROVAL', 'DECIDED')),
    ADD CONSTRAINT ck_objection_received_channel CHECK (received_channel IN ('BRANCH', 'ONLINE')),
    ADD CONSTRAINT ck_objection_supplement_round CHECK (supplement_round BETWEEN 0 AND 2),
    ADD CONSTRAINT ck_objection_supplement_due CHECK (supplement_round > 0 OR supplement_due IS NULL);

CREATE TABLE objection_supplement_request (
    supplement_request_id BIGINT        NOT NULL AUTO_INCREMENT,
    objection_id          BIGINT        NOT NULL,
    round                 TINYINT       NOT NULL COMMENT '보완 회차. 1 또는 2',
    reason                VARCHAR(500)  NOT NULL COMMENT '보완 요청 사유. 명의인에게 보인다',
    missing_documents     JSON          NOT NULL COMMENT '요청한 서류 이름 배열(최대 20개)',
    due_date              DATE          NOT NULL COMMENT '보완 기한',
    requested_by          BIGINT        NOT NULL COMMENT '요청한 관리자 customer_id',
    requested_at          DATETIME(6)   NOT NULL,
    submitted_at          DATETIME(6)   NULL COMMENT '명의인이 보완 자료를 낸 시각',
    submit_note           VARCHAR(1000) NULL COMMENT '명의인이 보완 제출 때 남긴 메모',

    PRIMARY KEY (supplement_request_id),
    UNIQUE KEY uk_objection_supplement_round (objection_id, round),
    CONSTRAINT fk_objection_supplement_objection FOREIGN KEY (objection_id) REFERENCES objection (objection_id),
    CONSTRAINT ck_objection_supplement_request_round CHECK (round BETWEEN 1 AND 2)
) ENGINE=InnoDB COMMENT='이의제기 보완 요청 (D-36)';

CREATE TABLE objection_review_session (
    review_session_id BIGINT      NOT NULL AUTO_INCREMENT,
    objection_id      BIGINT      NOT NULL,
    admin_id          BIGINT      NOT NULL COMMENT '상세 화면을 연 관리자 customer_id',
    started_at        DATETIME(6) NOT NULL COMMENT '상세 화면을 연 시각. 처리 시간 평가에 쓴다',

    PRIMARY KEY (review_session_id),
    KEY ix_objection_review_session_objection (objection_id, started_at),
    CONSTRAINT fk_objection_review_session_objection FOREIGN KEY (objection_id) REFERENCES objection (objection_id)
) ENGINE=InnoDB COMMENT='이의제기 심사 시작 기록 (D-36)';
