-- ====================================================================
-- V202608010950__create_limit.sql
-- 이체한도 (P1)

-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
-- ====================================================================

CREATE TABLE transfer_limit (
    customer_id    BIGINT      NOT NULL,
    one_time_limit BIGINT      NOT NULL,
    daily_limit    BIGINT      NOT NULL,
    version        BIGINT      NOT NULL DEFAULT 0,
    updated_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (customer_id),
    CONSTRAINT fk_tl_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id),
    -- LMT0004(1회 한도는 1일 한도를 초과할 수 없음)를 DB 레벨에서 보장
    CONSTRAINT ck_tl_order  CHECK (one_time_limit <= daily_limit),
    CONSTRAINT ck_tl_positive CHECK (one_time_limit > 0 AND daily_limit > 0)
) ENGINE=InnoDB COMMENT='이체한도 (P1 소유 경계 유지)';

CREATE TABLE transfer_limit_daily_usage (
    customer_id BIGINT      NOT NULL,
    usage_date  DATE        NOT NULL COMMENT 'KST 기준 영업일',
    used_amount BIGINT      NOT NULL DEFAULT 0 COMMENT 'SUCCESS 확정 건만 합산',
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (customer_id, usage_date),
    CONSTRAINT fk_tldu_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id),
    CONSTRAINT ck_tldu_used CHECK (used_amount >= 0)
) ENGINE=InnoDB COMMENT='일별 한도 사용액 (비관적 락 대상 1순위)';
