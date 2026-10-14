-- ====================================================================
-- V202610131000__add_gl_voucher_reference_key.sql
-- 전표 참조 키와 취소정정 전표 유형 (P3, PH-24)

-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
--
-- reference_key 는 원 거래번호다. 원장·이체와 전표를 잇고, "거래 1건당 전표 1건" 검증과
-- PH-28b 분개누락 탐지가 이 키로 원장과 조인한다. 같은 키로 두 번 기표하면 UNIQUE 위반으로 막는다.
-- REVERSAL 은 정정 체인(#548)의 취소정정 전표다. 원 전표와 같은 키를 쓰지 않고 자기 거래번호를 쓴다.
--
-- 기존 행은 키를 채운 뒤에 NOT NULL 을 건다.
--   - TRANSFER · PRODUCT_SUBSCRIPTION : PH-60 · PH-60b 시드가 description 에 남긴 원장 transaction_number
--   - OPENING                         : OPENING-{yyyyMMdd}(거래일)
-- 채우지 못한 행이 남으면 NOT NULL 변경이 실패해 기동이 멈춘다. 키 없는 전표를 조용히 두지 않기 위해서다.
--
-- 운영 규모(전표 약 323만 행, 2026-10-10 PH-60b 적재 뒤)에서 로컬 측정 약 1분 45초다
-- (mysql 8.4.10 · CPU 2 · 메모리 1g · 버퍼 풀 512M). 시간을 줄이려고 두 가지를 한다.
--   1. 채우기를 UPDATE 한 문장으로 한다. 유형별로 나누면 323만 행을 두 번 훑는다. 하루 단위로 나눈 버전은
--      8초 더 느렸고, 행 락은 페이지 단위로 잡혀 한 문장이어도 락 메모리가 크지 않다.
--   2. 제약 변경(NOT NULL · UNIQUE · CHECK)을 ALTER 하나로 합쳐 테이블 재구성을 한 번만 한다.
-- 운영은 로컬보다 느릴 것으로 보고 이 파일이 나가는 배포에서 CD 대기 시간을 늘린다(PH-24, #585).
-- ====================================================================

ALTER TABLE gl_voucher
    ADD COLUMN reference_key VARCHAR(40) NULL COMMENT '원 거래번호. (tx_type, reference_key) 유일' AFTER tx_type;

UPDATE gl_voucher
SET reference_key = CASE tx_type
        WHEN 'OPENING' THEN CONCAT('OPENING-', DATE_FORMAT(trade_date, '%Y%m%d'))
        ELSE description
    END
WHERE tx_type IN ('OPENING', 'TRANSFER', 'PRODUCT_SUBSCRIPTION')
  AND reference_key IS NULL;

ALTER TABLE gl_voucher
    MODIFY COLUMN reference_key VARCHAR(40) NOT NULL COMMENT '원 거래번호. (tx_type, reference_key) 유일',
    ADD UNIQUE KEY uk_gl_voucher_tx_type_reference_key (tx_type, reference_key),
    DROP CHECK ck_gl_voucher_tx_type,
    ADD CONSTRAINT ck_gl_voucher_tx_type
        CHECK (tx_type IN ('OPENING', 'TRANSFER', 'PRODUCT_SUBSCRIPTION', 'INTEREST', 'REVERSAL'));
