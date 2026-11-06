-- ====================================================================
-- V202611060400__create_objection_notice.sql
-- 대상 테이블: objection_notice (신규 · P3)
--
-- 이의제기 AI 심사 지원(D-36) 통지 기록(Plan D30). 실제로 보내지 않고 누구에게 어떤 통지를 어떤 수단으로 했어야
-- 하는지만 남겨 화면에 보인다. 접수와 보완 요청, 판정 확정과 같은 트랜잭션에서 쓴다.
-- 명의인은 문자(SMS), 피해자와 금감원, 관련 금융회사는 문서(DOCUMENT)로 기록한다.
-- ====================================================================

CREATE TABLE objection_notice (
    notice_id    BIGINT       NOT NULL AUTO_INCREMENT,
    objection_id BIGINT       NOT NULL,
    target       VARCHAR(12)  NOT NULL COMMENT 'HOLDER 명의인 / VICTIM 피해자 / FSS 금감원 / RELATED_BANK 관련 금융회사',
    kind         VARCHAR(20)  NOT NULL
        COMMENT 'RECEIPT / SUPPLEMENT_REQUEST / DECISION_ACCEPTED / DECISION_PARTIAL / DECISION_REJECTED / STOP_TERMINATED',
    channel      VARCHAR(10)  NOT NULL COMMENT 'SMS / DOCUMENT',
    basis        VARCHAR(100) NOT NULL COMMENT '통지 근거(법 조항, 당국 표준, 내부 기준)',
    recorded_at  DATETIME(6)  NOT NULL,

    PRIMARY KEY (notice_id),
    KEY ix_objection_notice_objection (objection_id, notice_id),
    CONSTRAINT fk_objection_notice_objection FOREIGN KEY (objection_id) REFERENCES objection (objection_id),
    CONSTRAINT ck_objection_notice_target CHECK (target IN ('HOLDER', 'VICTIM', 'FSS', 'RELATED_BANK')),
    CONSTRAINT ck_objection_notice_kind CHECK (kind IN (
        'RECEIPT', 'SUPPLEMENT_REQUEST', 'DECISION_ACCEPTED', 'DECISION_PARTIAL', 'DECISION_REJECTED', 'STOP_TERMINATED')),
    CONSTRAINT ck_objection_notice_channel CHECK (channel IN ('SMS', 'DOCUMENT'))
) ENGINE=InnoDB COMMENT='이의제기 통지 기록 (D-36)';
