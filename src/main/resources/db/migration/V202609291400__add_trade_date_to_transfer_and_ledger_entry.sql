-- ====================================================================
-- V202609291400__add_trade_date_to_transfer_and_ledger_entry.sql
-- 거래일(trade_date) 컬럼 추가 (P5, PH-41 / #472) — 값은 P4 가 10/16 에 대입한다
-- ====================================================================

-- NULL 허용: 기존 행을 발생일로 채우면 휴일 거래가 틀린 거래일을 갖는다. 대입 후 채우고 NOT NULL 로 좁힌다(Expand-Contract)
ALTER TABLE transfer
    ADD COLUMN trade_date DATE NULL COMMENT '거래일 — 귀속 영업일. BusinessDateProvider.today()' AFTER transferred_at,
    ALGORITHM = INSTANT;

-- 파티션 테이블이라 재구성 없이 추가되는지 INSTANT 로 강제한다 — 안 되면 마이그레이션이 실패해 드러난다
ALTER TABLE ledger_entry
    ADD COLUMN trade_date DATE NULL COMMENT '거래일 — 귀속 영업일. BusinessDateProvider.today()' AFTER occurred_at,
    ALGORITHM = INSTANT;
