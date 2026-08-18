-- ====================================================================
-- V202608181254__create_transfer_limit_history.sql
-- 이체한도 변경 이력 (P1)
--
-- REQ-TRSF-025 "변경 이력을 저장한다" 를 담는 전용 테이블이다.
-- 공용 audit_log 는 범용 감사 스키마라 1회·1일 한도의 변경 전후값을
-- 구조화해 담기 어렵고 인수기준을 테스트로 검증하기 어려워 분리한다.
-- 변경자·요청 IP 같은 감사 정보는 audit_log(P6) 의 책임이므로 두지 않는다.
--
-- CHECK 제약을 두지 않는 이유: 여기 적재되는 값은 transfer_limit 의
-- ck_tl_positive·ck_tl_order 를 이미 통과한 데이터의 복사본이다.
-- 인덱스를 두지 않는 이유: 이력 조회 요구사항이 아직 없고, FK 가
-- customer_id 인덱스를 자동 생성한다. 조회 API 가 생기면 그때 추가한다.
-- ====================================================================

CREATE TABLE transfer_limit_history (
    history_id            BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id           BIGINT      NOT NULL,
    before_one_time_limit BIGINT      NOT NULL COMMENT '변경 전 1회 이체한도',
    after_one_time_limit  BIGINT      NOT NULL COMMENT '변경 후 1회 이체한도',
    before_daily_limit    BIGINT      NOT NULL COMMENT '변경 전 1일 이체한도',
    after_daily_limit     BIGINT      NOT NULL COMMENT '변경 후 1일 이체한도',
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    PRIMARY KEY (history_id),
    CONSTRAINT fk_tlh_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id)
) ENGINE=InnoDB COMMENT='이체한도 변경 이력 (P1 소유 경계 유지)';
