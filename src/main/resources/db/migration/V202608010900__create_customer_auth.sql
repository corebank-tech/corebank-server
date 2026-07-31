-- ====================================================================
-- V202608010900__create_customer_auth.sql
-- 고객·인증·약관·알림 (P6)

-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
-- ====================================================================

CREATE TABLE customer (
    customer_id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id               VARCHAR(20)  NOT NULL COMMENT '로그인 아이디',
    password_hash         CHAR(60)     NOT NULL COMMENT 'BCrypt',
    user_name             VARCHAR(50)  NOT NULL,
    birth_date            DATE         NOT NULL,
    email                 VARCHAR(100) NOT NULL,
    phone_number          VARCHAR(11)  NOT NULL COMMENT '하이픈 없음',
    login_failure_count   TINYINT      NOT NULL DEFAULT 0 COMMENT '5회 시 잠금 (ATH0102)',
    account_locked        BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order_type    VARCHAR(20)  NOT NULL DEFAULT 'OPENED_DATE_ASC' COMMENT 'CUSTOM / OPENED_DATE_ASC',
    -- login_history 흡수: 대시보드가 쓰는 값은 직전·현재 2건뿐
    last_login_at         DATETIME(6)  NULL COMMENT '대시보드 currentLoginAt',
    last_login_ip         VARCHAR(45)  NULL COMMENT '대시보드 currentLoginIp',
    previous_login_at     DATETIME(6)  NULL COMMENT '대시보드 previousLoginAt',
    password_changed_at   DATETIME(6)  NULL,
    joined_at             DATETIME(6)  NOT NULL,
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (customer_id),
    UNIQUE KEY uk_customer_user_id (user_id),
    UNIQUE KEY uk_customer_email (email)
) ENGINE=InnoDB COMMENT='고객';

CREATE TABLE terms (
    terms_id      BIGINT       NOT NULL AUTO_INCREMENT,
    terms_code    VARCHAR(30)  NOT NULL,
    version       VARCHAR(10)  NOT NULL,
    terms_type    VARCHAR(20)  NOT NULL COMMENT 'SIGNUP / PRODUCT',
    title         VARCHAR(200) NOT NULL,
    content       TEXT         NOT NULL,
    is_required   BOOLEAN      NOT NULL DEFAULT FALSE,
    view_required BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '전문 열람 강제 (PRD0005)',
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (terms_id),
    UNIQUE KEY uk_terms_code_version (terms_code, version)
) ENGINE=InnoDB COMMENT='약관 (회원가입·상품 공용)';

CREATE TABLE customer_terms_agreement (
    agreement_id BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id  BIGINT      NOT NULL,
    terms_id     BIGINT      NOT NULL,
    agreed_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (agreement_id),
    UNIQUE KEY uk_cta (customer_id, terms_id),
    CONSTRAINT fk_cta_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id),
    CONSTRAINT fk_cta_terms    FOREIGN KEY (terms_id)    REFERENCES terms (terms_id)
) ENGINE=InnoDB COMMENT='회원가입 약관 동의';

-- 이메일 인증 · 비밀번호 재설정 · OTP 를 하나로 통합.
-- 셋 다 [코드 발급 -> 검증 -> 만료 -> 1회용] 구조가 동일하고,
-- 명세상 식별자가 모두 String(EVF_ / PRR_ / OTP_REQ_ 접두어)이라 PK 타입 충돌이 없다.
CREATE TABLE verification_request (
    verification_request_id VARCHAR(64) NOT NULL COMMENT 'EVF_ / PRR_ / OTP_REQ_ 접두어 + CSPRNG 난수',
    purpose                 VARCHAR(24) NOT NULL COMMENT 'SIGN_UP / EMAIL_CHANGE / PASSWORD_RESET / OTP_TRANSACTION',
    customer_id             BIGINT      NULL COMMENT '회원가입 전에는 NULL',
    target                  VARCHAR(100) NULL COMMENT '이메일 주소 등 인증 대상',
    code_hash               CHAR(60)    NOT NULL COMMENT '인증번호·OTP 단방향 해시 (REQ-NFR-009)',
    transaction_type        VARCHAR(32) NULL COMMENT 'OTP 전용. IMMEDIATE_TRANSFER 등',
    transaction_data        JSON        NULL COMMENT 'OTP 전용. 거래 내용 변조 검증',
    error_count             TINYINT     NOT NULL DEFAULT 0 COMMENT '5회 초과 시 OTP0103',
    locked                  BOOLEAN     NOT NULL DEFAULT FALSE,
    used                    BOOLEAN     NOT NULL DEFAULT FALSE,
    verified_at             DATETIME(6) NULL,
    expires_at              DATETIME(6) NOT NULL COMMENT '발급 + 180초',
    created_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (verification_request_id),
    KEY ix_vr_customer (customer_id, purpose, created_at DESC),
    KEY ix_vr_target (target, purpose, created_at DESC),
    CONSTRAINT fk_vr_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id)
) ENGINE=InnoDB COMMENT='인증 요청 (이메일·비밀번호재설정·OTP 통합). *AuthToken 은 Redis';

CREATE TABLE notification (
    notification_id BIGINT       NOT NULL AUTO_INCREMENT,
    customer_id     BIGINT       NOT NULL,
    type            VARCHAR(24)  NOT NULL COMMENT 'TRANSFER / SCHEDULED_TRANSFER / AUTO_TRANSFER / PRODUCT_SUBSCRIPTION',
    ref_id          BIGINT       NULL COMMENT '다형 참조: TRANSFER->transfer.transfer_id / SCHEDULED_TRANSFER->scheduled_transfer_id / AUTO_TRANSFER->auto_transfer_execution.execution_id / PRODUCT_SUBSCRIPTION->subscription_id',
    title           VARCHAR(100) NOT NULL,
    content         VARCHAR(500) NOT NULL,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at         DATETIME(6)  NULL,
    occurred_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (notification_id),
    KEY ix_noti_customer (customer_id, is_read, occurred_at DESC),
    CONSTRAINT fk_noti_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id)
) ENGINE=InnoDB COMMENT='알림';
