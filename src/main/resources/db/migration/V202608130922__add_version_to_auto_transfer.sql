-- auto_transfer.version: 낙관적 락(@Version)용 컬럼.
-- 배치가 루프 시작 시점에 읽어둔 stale AutoTransfer를 전체 필드로 덮어쓰기 저장하면서,
-- 배치 처리 중 고객이 PATCH로 바꾼 변경사항이 조용히 되돌아갈 수 있는 문제(PR2 리뷰 R1)를 막기 위함.
ALTER TABLE auto_transfer
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락' AFTER updated_at;