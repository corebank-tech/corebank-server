-- ====================================================================
-- V202611051500__create_unregistered_login_failure.sql
-- 가입되지 않은 아이디의 로그인 실패 횟수 (P1, PH-08 / #900)
--
-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
--
-- 가입 계정과 같은 기준(아이디)으로 실패를 세어 5회째에 같은 잠금 응답을 주기 위한 테이블이다.
-- 아이디 원문은 저장하지 않고 소문자 정규화 후 HMAC-SHA256 hex 만 둔다.
-- 유효성은 expires_at 으로 판정하고, 만료 행 정리 배치는 저장 공간 회수용이다.
-- ====================================================================

CREATE TABLE unregistered_login_failure (
    user_id_hash   CHAR(64)    NOT NULL COMMENT '소문자 정규화한 로그인 아이디의 HMAC-SHA256 hex',
    failure_count  INT         NOT NULL COMMENT '만료 전 누적 실패 횟수',
    last_failed_at DATETIME(6) NOT NULL COMMENT '마지막 실패 시각',
    expires_at     DATETIME(6) NOT NULL COMMENT '마지막 실패 후 30일',
    PRIMARY KEY (user_id_hash),
    INDEX ix_unregistered_login_failure_expires (expires_at)
) ENGINE=InnoDB COMMENT='가입되지 않은 아이디의 로그인 실패 횟수 (PH-08)';
