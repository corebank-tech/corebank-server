-- ====================================================================
-- V202611051600__create_agent_run_tables.sql
-- 대상 테이블: agent_run, agent_run_step, review_draft (신규 · P3)
--
-- 이의제기 AI 심사 지원(D-36) PA-05. Agent 실행 한 번, 그 안의 단계 이벤트, Agent 가 낸 판정안.
-- 형식은 docs/phase2/agent_run_step_schema.md 를 따른다.
-- 대기열은 별도 큐 없이 agent_run 의 QUEUED 행이다. 워커는 조건부 UPDATE 로 가져가고(점유 5분),
-- 3회 실패하면 FAILED 로 끝난다(Plan D15).
-- ====================================================================

CREATE TABLE agent_run (
    run_id          BIGINT       NOT NULL AUTO_INCREMENT,
    objection_id    BIGINT       NOT NULL,
    status          VARCHAR(12)  NOT NULL COMMENT 'QUEUED / RUNNING / COMPLETED / FAILED',
    attempt         INT          NOT NULL DEFAULT 0 COMMENT '가져간 횟수. 3회째 실패하면 FAILED',
    lease_until     DATETIME(6)  NULL COMMENT '워커 점유 만료 시각. RUNNING 일 때만',
    model           VARCHAR(60)  NULL,
    prompt_version  VARCHAR(30)  NULL,
    input_tokens    BIGINT       NULL,
    output_tokens   BIGINT       NULL,
    cost_micro_usd  BIGINT       NULL COMMENT '비용, 백만분의 1달러 정수. BigDecimal 은 금리 전용이다',
    requested_by    BIGINT       NOT NULL COMMENT '분석을 넣은 관리자 customer_id',
    queued_at       DATETIME(6)  NOT NULL,
    started_at      DATETIME(6)  NULL,
    ended_at        DATETIME(6)  NULL,
    failure_reason  VARCHAR(200) NULL,

    PRIMARY KEY (run_id),
    KEY ix_agent_run_objection (objection_id, run_id),
    KEY ix_agent_run_status (status, queued_at),
    CONSTRAINT fk_agent_run_objection FOREIGN KEY (objection_id) REFERENCES objection (objection_id),
    CONSTRAINT ck_agent_run_status CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_agent_run_attempt CHECK (attempt BETWEEN 0 AND 3),
    CONSTRAINT ck_agent_run_lease CHECK ((status = 'RUNNING') = (lease_until IS NOT NULL))
) ENGINE=InnoDB COMMENT='이의제기 Agent 실행 (D-36)';

CREATE TABLE agent_run_step (
    step_id     BIGINT       NOT NULL AUTO_INCREMENT,
    run_id      BIGINT       NOT NULL,
    seq         INT          NOT NULL COMMENT '실행 안 순번. 1부터 빈 번호 없이. 같은 seq 재전송은 무시한다',
    type        VARCHAR(20)  NOT NULL,
    title       VARCHAR(200) NOT NULL COMMENT '화면 한 줄. 가명 처리한 값만',
    summary     VARCHAR(500) NULL,
    detail      JSON         NULL COMMENT '종류별 상세. 가명 처리한 값만',
    status      VARCHAR(10)  NOT NULL COMMENT 'RUNNING / DONE / FAILED',
    started_at  DATETIME(6)  NOT NULL,
    ended_at    DATETIME(6)  NULL,

    PRIMARY KEY (step_id),
    UNIQUE KEY uk_agent_run_step_seq (run_id, seq),
    CONSTRAINT fk_agent_run_step_run FOREIGN KEY (run_id) REFERENCES agent_run (run_id),
    CONSTRAINT ck_agent_run_step_seq CHECK (seq >= 1),
    CONSTRAINT ck_agent_run_step_type CHECK (type IN (
        'CLAIMED', 'CONTEXT_LOADED', 'OCR', 'TOOL_CALL', 'CLAIM_EXTRACTED',
        'CONTRADICTION', 'DRAFT_SUBMITTED', 'GATE_EVALUATED', 'FAILED')),
    CONSTRAINT ck_agent_run_step_status CHECK (status IN ('RUNNING', 'DONE', 'FAILED'))
) ENGINE=InnoDB COMMENT='Agent 실행 단계 이벤트 (D-36)';

CREATE TABLE review_draft (
    review_draft_id BIGINT       NOT NULL AUTO_INCREMENT,
    run_id          BIGINT       NOT NULL,
    objection_id    BIGINT       NOT NULL,
    branch          VARCHAR(16)  NOT NULL COMMENT 'Agent 가 낸 갈래. 해제 근거가 아니다(D9)',
    content         JSON         NOT NULL COMMENT '근거 거래, 누락 소명자료, 주장, 모순, 추가 확인, 안내문 초안',
    submitted_at    DATETIME(6)  NOT NULL,

    PRIMARY KEY (review_draft_id),
    UNIQUE KEY uk_review_draft_run (run_id),
    KEY ix_review_draft_objection (objection_id, review_draft_id),
    CONSTRAINT fk_review_draft_run FOREIGN KEY (run_id) REFERENCES agent_run (run_id),
    CONSTRAINT fk_review_draft_objection FOREIGN KEY (objection_id) REFERENCES objection (objection_id),
    CONSTRAINT ck_review_draft_branch
        CHECK (branch IN ('FRAUD_PATTERN', 'NORMAL_ACCEPT', 'NORMAL_PARTIAL', 'UNDETERMINED'))
) ENGINE=InnoDB COMMENT='Agent 판정안 (D-36)';
