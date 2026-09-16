-- scheduled_transfer.version: 낙관적 락(@Version)용 컬럼.
-- 다건 취소가 WAITING으로 읽어둔 건을 배치가 PROCESSING으로 선점한 뒤,
-- 취소가 그 결과를 예외 없이 CANCELED로 덮어쓰는 문제(PR #335 리뷰 R2)를 막기 위함.
ALTER TABLE scheduled_transfer
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락' AFTER failure_reason;
