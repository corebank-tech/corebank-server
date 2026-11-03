-- ====================================================================
-- V202611021100__create_delayed_transfer_setting.sql
-- 대상 테이블: delayed_transfer_setting (신규 · P4)
--
-- 고객별 지연이체 설정(PH-06, #772). 근거: 전자금융거래법 제13조제2항, 시행령 제9조의2.
-- 행이 없으면 꺼진 것으로 본다. 처음 저장할 때 INSERT하므로 기존 고객 백필이 필요 없다.
-- 지연시간 최소 3시간은 금감원 안내·은행 공통 기준이고, 최대 24시간은 우리 기준이다.
-- ====================================================================
CREATE TABLE delayed_transfer_setting
(
    customer_id      BIGINT      NOT NULL COMMENT '고객. 고객당 1행',
    enabled          BOOLEAN     NOT NULL COMMENT '지연이체 사용 여부',
    threshold_amount BIGINT      NOT NULL COMMENT '기준금액(원). 이 금액 이상인 즉시이체를 지연한다',
    delay_hours      INT         NOT NULL COMMENT '지연시간(시간). 요청 시각부터 이만큼 뒤에 실행한다',
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (customer_id),
    CONSTRAINT ck_delayed_transfer_setting_threshold CHECK (threshold_amount > 0),
    CONSTRAINT ck_delayed_transfer_setting_delay CHECK (delay_hours BETWEEN 3 AND 24)
) ENGINE=InnoDB COMMENT='고객별 지연이체 설정 (PH-06, #772)';
