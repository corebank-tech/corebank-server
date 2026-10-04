-- ====================================================================
-- V202610042330__add_transfer_correction_chain.sql
-- 이체 정정 체인 컬럼 추가 (P4, PH-80 S1분 / #548)
--
-- 정정 체인: 원거래 무효화 → 취소정정 거래 INSERT → 정상거래 INSERT. DELETE 0건.
-- 원장 쪽 반대기표 장치(ledger_entry.reversed · reversal_id)는 이미 있고, 이 파일은 transfer 쪽만 채운다.
-- ====================================================================

-- 일반 거래는 셋 다 NULL이다. invalidated_at은 감사 시 "언제 무효가 됐나"를 남기려고 boolean 대신 시각으로 둔다
ALTER TABLE transfer
    ADD COLUMN invalidated_at  DATETIME(6) NULL COMMENT '정정 체인으로 무효화된 시각. NULL이면 유효' AFTER trade_date,
    ADD COLUMN ref_transfer_id BIGINT      NULL COMMENT '정정 체인의 원거래 transfer_id'            AFTER invalidated_at,
    ADD COLUMN correction_type VARCHAR(12) NULL COMMENT 'REVERSAL(취소정정) / REPOST(정상거래)'     AFTER ref_transfer_id,
    ALGORITHM = INSTANT;

-- 원거래 하나에 취소정정·정상거래는 각각 1건뿐이다. 동시 정정 요청을 조회-후-검증이 아니라 DB가 막는다(TOCTOU)
-- 왼쪽 컬럼이 ref_transfer_id라 원거래로 체인을 찾는 조회와 FK 인덱스를 겸한다
ALTER TABLE transfer
    ADD UNIQUE KEY uk_transfer_correction (ref_transfer_id, correction_type),
    ADD CONSTRAINT fk_transfer_ref FOREIGN KEY (ref_transfer_id) REFERENCES transfer (transfer_id),
    ADD CONSTRAINT ck_transfer_correction_type CHECK (correction_type IN ('REVERSAL', 'REPOST')),
    ADD CONSTRAINT ck_transfer_correction_ref CHECK ((correction_type IS NULL) = (ref_transfer_id IS NULL));
