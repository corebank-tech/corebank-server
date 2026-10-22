-- ====================================================================
-- V202610221600__widen_gl_voucher_tx_type_maturity_termination.sql
-- 만기해지와 중도해지 원리금 전표 유형 (P3, #713)
--
-- 예적금 원리금 이체 패턴(gl_journal_patterns.md §3-7)의 전표 유형을 CHECK에 더한다.
--   - MATURITY    (MAT) 만기해지 원리금 이체(PH-15) — 차 20100 예수금 / 대 20100 예수금
--   - TERMINATION (TRM) 중도해지 원리금 이체(PH-16) — 차 20100 예수금 / 대 20100 예수금
-- 참조 키는 transfer.api.LedgerDepositUseCase.transferBetween() 이 돌려준 원장 거래번호다.
--
-- CHECK 교체는 INPLACE가 지원되지 않아 테이블을 다시 쓴다(ALGORITHM=COPY). 그동안 전표 쓰기가 막혀
-- 같은 트랜잭션에서 전표를 쓰는 이체도 멈춘다. 직전 V202610201222 측정 기준 320만 행 약 23초다.
-- ====================================================================

ALTER TABLE gl_voucher
    DROP CHECK ck_gl_voucher_tx_type,
    ADD CONSTRAINT ck_gl_voucher_tx_type
        CHECK (tx_type IN ('OPENING', 'TRANSFER', 'PRODUCT_SUBSCRIPTION', 'INTEREST', 'REVERSAL',
                           'INTERBANK_TRANSFER', 'INTERBANK_SETTLEMENT', 'MATURITY', 'TERMINATION'));
