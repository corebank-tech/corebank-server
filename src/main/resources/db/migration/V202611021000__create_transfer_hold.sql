-- ====================================================================
-- V202611021000__create_transfer_hold.sql
-- 대상 테이블: transfer_hold (신규 · P4)
--
-- 이체 보류(#772, transfer_seam.md §9). 훅 C가 보류로 판정한 즉시이체의 원본 요청을 기표 없이 담아 둔다.
-- 실행하면 원본 그대로 execute()를 한 번 부르고 transfer(source_type=HELD, source_id=이 행)가 생긴다.
-- 인증 토큰 원문은 저장하지 않는다. 상태 전이는 조건부 UPDATE로 하므로 version 컬럼을 두지 않는다.
-- ====================================================================
CREATE TABLE transfer_hold
(
    transfer_hold_id        BIGINT       NOT NULL AUTO_INCREMENT,
    customer_id             BIGINT       NOT NULL COMMENT '요청한 고객',
    withdrawal_account_id   BIGINT       NOT NULL COMMENT '출금계좌 ID',
    deposit_account_number  VARCHAR(16)  NOT NULL COMMENT '입금 계좌번호(당행, 숫자만)',
    amount                  BIGINT       NOT NULL COMMENT '이체금액(원)',
    my_passbook_memo        VARCHAR(10)  NULL COMMENT '내 통장 표시내용',
    recipient_passbook_memo VARCHAR(10)  NULL COMMENT '받는 분 통장 표시내용',
    channel                 CHAR(2)      NOT NULL COMMENT '요청 채널. 실행할 때도 이 채널로 채번한다',
    flow_id                 VARCHAR(64)  NULL COMMENT 'FE 행동 흐름 식별자',
    policy_code             VARCHAR(30)  NOT NULL COMMENT '보류를 정한 정책 코드',
    release_mode            VARCHAR(12)  NOT NULL COMMENT 'AT_TIME / ON_APPROVAL',
    release_at              DATETIME(6)  NULL COMMENT 'AT_TIME일 때 실행할 시각',
    status                  VARCHAR(10)  NOT NULL COMMENT 'HELD / EXECUTING / EXECUTED / FAILED / REJECTED / CANCELLED / EXPIRED',
    authenticated_at        DATETIME(6)  NOT NULL COMMENT '계좌비밀번호·OTP 토큰을 소비한 시각',
    held_at                 DATETIME(6)  NOT NULL COMMENT '보류 저장 시각',
    transfer_id             BIGINT       NULL COMMENT '실행으로 생긴 transfer 행. 실행 전이면 NULL',
    actor_id                BIGINT       NULL COMMENT '실행·거절·취소한 주체. 스케줄러 실행은 NULL',
    reason_code             VARCHAR(30)  NULL COMMENT '거절 사유 코드',
    executing_at            DATETIME(6)  NULL COMMENT 'EXECUTING으로 선점한 시각. 멈춘 실행을 찾는 기준',
    completed_at            DATETIME(6)  NULL COMMENT '종료 상태가 된 시각',
    PRIMARY KEY (transfer_hold_id),
    UNIQUE KEY uk_transfer_hold_transfer (transfer_id),
    KEY ix_transfer_hold_status_release (status, release_at),
    KEY ix_transfer_hold_customer (customer_id, held_at),
    CONSTRAINT ck_transfer_hold_amount CHECK (amount > 0),
    CONSTRAINT ck_transfer_hold_release CHECK ((release_mode = 'AT_TIME') = (release_at IS NOT NULL))
) ENGINE=InnoDB COMMENT='이체 보류 (#772, transfer_seam.md §9)';
