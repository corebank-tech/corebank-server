-- ====================================================================
-- V202610151100__create_ledger_reconciliation_mismatch.sql
-- 원장-잔액 대사 불일치 목록 (P4, PH-38 / #613)

-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
--
-- COB 대사 스텝이 불일치 계좌를 영업일별로 남기고, 관리자 목록 API(FE ADM-04)가 읽는다.
-- 같은 영업일을 다시 대사하면 그날 행을 지우고 새 결과로 바꾼다 — 재실행 이력은 batch_execution_history 몫이다.
-- 대사는 보고만 하고 정정하지 않으므로 처리 상태 컬럼을 두지 않는다.
-- account 에 FK 를 걸지 않는다 — 감사용 기록이라 계좌 해지·정리와 무관하게 남아야 한다.
-- ====================================================================

CREATE TABLE ledger_reconciliation_mismatch (
    ledger_reconciliation_mismatch_id BIGINT      NOT NULL AUTO_INCREMENT,
    business_date                     DATE        NOT NULL COMMENT '대사한 COB 의 마감 대상 영업일 (ADR-0004 COB_DATE)',
    account_id                        BIGINT      NOT NULL,
    ledger_balance                    BIGINT      NOT NULL COMMENT '원장 누적 합계 SUM(DEPOSIT) - SUM(WITHDRAWAL)',
    account_balance                   BIGINT      NOT NULL COMMENT '대사 시점 account.balance',
    detected_at                       DATETIME(6) NOT NULL COMMENT '대사가 불일치를 확인한 시각',
    PRIMARY KEY (ledger_reconciliation_mismatch_id),
    UNIQUE KEY uk_ledger_reconciliation_mismatch_date_account (business_date, account_id)
) ENGINE=InnoDB COMMENT='원장-잔액 대사 불일치 목록 (#613)';
