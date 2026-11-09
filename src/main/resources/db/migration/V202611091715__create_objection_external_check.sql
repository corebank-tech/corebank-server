-- ====================================================================
-- V202611091715__create_objection_external_check.sql
-- 대상 테이블: objection_external_check (신규 · P3)
--
-- 이의제기 AI 심사 지원(D-36) PA-10 외부 확인 모의. 수사기관(POLICE)과 관련 금융회사(PEER_BANK)에 확인을 요청하고
-- 회신을 받는다. 모의라 요청 즉시 담당자가 지정한 회신으로 REPLIED 가 된다. 회신은 해제 게이트의 외부 확인 사실이다
-- (부정 회신은 불수용 후보, 명의인 무관은 수용 후보). NO_RECORD 회신은 게이트에 영향이 없다.
-- 같은 기관에는 이의제기당 한 번만 요청한다. 중복 요청은 기존 행을 돌려준다.
-- ====================================================================

CREATE TABLE objection_external_check (
    external_check_id BIGINT      NOT NULL AUTO_INCREMENT,
    objection_id      BIGINT      NOT NULL,
    agency            VARCHAR(10) NOT NULL COMMENT 'POLICE 수사기관 / PEER_BANK 관련 금융회사',
    status            VARCHAR(10) NOT NULL COMMENT 'REQUESTED 요청 / REPLIED 회신',
    reply             VARCHAR(24) NULL
        COMMENT 'INVOLVEMENT_SUSPECTED 가담 의심 / DOCUMENT_FORGED 서류 위조 판명 / OWNER_UNRELATED 명의인 무관 / NO_RECORD 해당 없음',
    requested_by      BIGINT      NOT NULL COMMENT '요청한 관리자 customer_id',
    requested_at      DATETIME(6) NOT NULL,
    replied_at        DATETIME(6) NULL,

    PRIMARY KEY (external_check_id),
    UNIQUE KEY uk_objection_external_check_agency (objection_id, agency),
    CONSTRAINT fk_objection_external_check_objection FOREIGN KEY (objection_id) REFERENCES objection (objection_id),
    CONSTRAINT ck_objection_external_check_agency CHECK (agency IN ('POLICE', 'PEER_BANK')),
    CONSTRAINT ck_objection_external_check_status CHECK (status IN ('REQUESTED', 'REPLIED')),
    CONSTRAINT ck_objection_external_check_reply CHECK (
        reply IS NULL OR reply IN ('INVOLVEMENT_SUSPECTED', 'DOCUMENT_FORGED', 'OWNER_UNRELATED', 'NO_RECORD')),
    CONSTRAINT ck_objection_external_check_replied CHECK (
        (status = 'REPLIED') = (reply IS NOT NULL AND replied_at IS NOT NULL))
) ENGINE=InnoDB COMMENT='이의제기 외부 확인 요청과 회신(모의) (D-36)';
