-- ====================================================================
-- V202609281930__create_business_date_and_holiday.sql
-- 영업일 · 휴일 달력 (P5, PH-40 / #471)
--
-- 영업일은 시계가 아니라 DB에 저장된 논리 업무일자다
-- 두 테이블로 나눈다 — 매일 바뀌는 값 1개(business_date)와
-- 한 번 넣고 거의 안 바뀌는 목록(holiday)은 성격이 다르다.
-- 주말은 테이블에 넣지 않고 요일로 판정한다.
-- ====================================================================
CREATE TABLE business_date
(
    date_type     VARCHAR(20) NOT NULL COMMENT '영업일 종류. 2차는 BUSINESS_DATE 하나',
    business_date DATE        NOT NULL COMMENT '현재 영업일',
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (date_type)
) ENGINE=InnoDB COMMENT='현재 영업일 (PH-40)';

CREATE TABLE holiday
(
    holiday_date DATE        NOT NULL,
    holiday_name VARCHAR(50) NOT NULL COMMENT '예: 개천절, 대체공휴일(추석)',
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (holiday_date)
) ENGINE=InnoDB COMMENT='휴일 달력 — 주말 제외 공휴일만 (PH-40)';

-- 초기 영업일. NOW()/CURRENT_DATE 는 RDS 가 UTC 라 날짜가 어긋날 수 있어 직접 적는다.
-- 이후 값은 임시 전환 스케줄러가 달력 날짜에 맞춘다 (#471).
INSERT INTO business_date (date_type, business_date)
VALUES ('BUSINESS_DATE', '2026-10-02');

