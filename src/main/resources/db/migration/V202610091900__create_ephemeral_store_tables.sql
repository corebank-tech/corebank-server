-- ====================================================================
-- V202610091900__create_ephemeral_store_tables.sql
-- Redis에 있던 일회성 인증 토큰·OTP 발급 잠금·약관 열람 이력을 MySQL로 옮긴다 (P4, PH-101-① / #580)

-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
--
-- 유효성은 항상 expires_at 으로 판정한다. 만료 행 정리 배치는 저장 공간 회수용이다.
-- 길어야 30분 사는 데이터라 customer 에 FK 를 걸지 않는다 (Redis 에도 참조 무결성이 없었다).
-- ====================================================================

CREATE TABLE auth_token (
    auth_token_id BIGINT      NOT NULL AUTO_INCREMENT,
    purpose       VARCHAR(30) NOT NULL COMMENT 'TERMS_AUTH / USER_ID_CHECK / EMAIL_VERIFICATION / ACCOUNT_AUTH / TEMP_SIGNUP / OTP_AUTH / ACCOUNT_PASSWORD_AUTH',
    token_hash    CHAR(64)    NOT NULL COMMENT '토큰 원문의 SHA-256 16진수. 원문은 저장하지 않는다',
    customer_id   BIGINT      NULL COMMENT '로그인 고객 토큰만. 회원가입 토큰은 NULL',
    payload       TEXT        COLLATE utf8mb4_bin NOT NULL COMMENT 'JSON 문자열. 값 일치 소비를 위해 바이트 단위로 비교한다',
    claim_id      CHAR(36)    NULL COMMENT '임시가입 토큰을 선점한 가입 완료 요청 ID',
    created_at    DATETIME(6) NOT NULL,
    expires_at    DATETIME(6) NOT NULL,
    consumed_at   DATETIME(6) NULL COMMENT '소비 시각. NULL 이면 아직 쓰지 않은 토큰',
    PRIMARY KEY (auth_token_id),
    UNIQUE KEY uk_auth_token_hash_purpose (token_hash, purpose),
    KEY ix_auth_token_expires (expires_at)
) ENGINE=InnoDB COMMENT='일회성 인증 토큰 (#580)';

CREATE TABLE otp_issue_lock (
    customer_id BIGINT      NOT NULL,
    owner_id    CHAR(36)    NOT NULL COMMENT '잠금을 잡은 발급 요청 ID. 주인만 해제할 수 있다',
    expires_at  DATETIME(6) NOT NULL COMMENT '지나면 다른 요청이 다시 잡을 수 있다',
    PRIMARY KEY (customer_id)
) ENGINE=InnoDB COMMENT='고객별 OTP 발급 잠금 (#580)';

CREATE TABLE terms_view_history (
    customer_id BIGINT      NOT NULL,
    terms_id    BIGINT      NOT NULL,
    viewed_at   DATETIME(6) NOT NULL COMMENT '마지막 열람 시각',
    expires_at  DATETIME(6) NOT NULL COMMENT '열람 인정 만료 시각 (열람 후 30분)',
    PRIMARY KEY (customer_id, terms_id)
) ENGINE=InnoDB COMMENT='상품 약관 열람 이력 (#580)';
