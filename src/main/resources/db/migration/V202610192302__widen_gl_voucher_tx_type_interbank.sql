-- ====================================================================
-- V202610192302__widen_gl_voucher_tx_type_interbank.sql
-- 타행 미결제 전표 유형 (P4, PH-33-② / #654)
--
-- 타행이체 미결제 2패턴(gl_journal_patterns.md §3-5)의 전표 유형을 CHECK에 더한다.
--   - INTERBANK_TRANSFER   (IBT) 송신 요청 — 차 20100 예수금 / 대 10200 미결제타점권
--   - INTERBANK_SETTLEMENT (IBS) 확정 해소 — 차 10200 / 대 10100(성공) 또는 20100(실패, 되돌림)
-- 두 전표는 같은 원 거래번호를 참조 키로 쓴다. 유형이 달라 (tx_type, reference_key) 유일 제약에 걸리지 않는다.
--
-- CHECK 교체는 INPLACE가 지원되지 않아 테이블을 다시 쓴다(ALGORITHM=COPY). 그동안 전표 쓰기가 막혀
-- 같은 트랜잭션에서 전표를 쓰는 이체도 멈춘다. 로컬 측정 320만 행 약 23초
-- (mysql 8.4.10 · 버퍼 풀 1G · 운영보다 컬럼이 적은 측정용 테이블). 운영은 더 걸린다.
-- ====================================================================

ALTER TABLE gl_voucher
    DROP CHECK ck_gl_voucher_tx_type,
    ADD CONSTRAINT ck_gl_voucher_tx_type
        CHECK (tx_type IN ('OPENING', 'TRANSFER', 'PRODUCT_SUBSCRIPTION', 'INTEREST', 'REVERSAL',
                           'INTERBANK_TRANSFER', 'INTERBANK_SETTLEMENT'));
