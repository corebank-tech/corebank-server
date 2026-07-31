-- ====================================================================
-- V202608010930__create_ledger.sql
-- 거래번호 채번·이체·원장 (P4)

-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
-- ====================================================================

-- 거래번호(CHAR(20) = YYYYMMDD + 채널2 + 일련10) 의 일련번호를 채번한다.
-- 일자·채널별로 매일 0 부터 다시 시작하므로 AUTO_INCREMENT 로는 만들 수 없고,
-- 동시 이체에서 번호가 겹치면 원장 짝(2행)이 깨지므로 별도 동시성 자원으로 둔다.
--   UPDATE transaction_sequence SET last_seq = LAST_INSERT_ID(last_seq + 1)
--    WHERE seq_date = ? AND channel = ?;   -- 행 단위 락, SELECT LAST_INSERT_ID() 로 회수
CREATE TABLE transaction_sequence (
    seq_date   DATE        NOT NULL COMMENT 'KST 기준 영업일',
    channel    CHAR(2)     NOT NULL COMMENT 'WB / BT',
    last_seq   BIGINT      NOT NULL DEFAULT 0 COMMENT '해당 일자·채널의 마지막 일련번호',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (seq_date, channel),
    CONSTRAINT ck_txseq_range CHECK (last_seq BETWEEN 0 AND 9999999999)
) ENGINE=InnoDB COMMENT='거래번호 일련번호 채번 (비관적 락 대상 0순위)';

CREATE TABLE transfer (
    transfer_id              BIGINT       NOT NULL AUTO_INCREMENT,
    transaction_number       CHAR(20)     NOT NULL COMMENT 'YYYYMMDD + 채널2 + 일련10',
    withdrawal_account_id    BIGINT       NOT NULL,
    deposit_account_id       BIGINT       NOT NULL COMMENT '원장 입금행의 근거. 1차는 당행 전용이라 NOT NULL',
    deposit_account_number   CHAR(12)     NOT NULL,
    payee_name               VARCHAR(50)  NOT NULL COMMENT '거래 시점 스냅샷',
    amount                   BIGINT       NOT NULL,
    fee                      BIGINT       NOT NULL DEFAULT 0 COMMENT '당행 0 고정 (POL-028)',
    transfer_type            VARCHAR(12)  NOT NULL COMMENT 'IMMEDIATE / SCHEDULED / AUTO',
    channel                  CHAR(2)      NOT NULL COMMENT 'WB / BT',
    status                   VARCHAR(12)  NOT NULL COMMENT 'SUCCESS / ERROR / PROCESSING',
    source_type              VARCHAR(12)  NULL COMMENT 'SCHEDULED / AUTO',
    source_id                BIGINT       NULL,
    my_passbook_memo         VARCHAR(10)  NULL COMMENT 'TRF0005 최대 10자',
    recipient_passbook_memo  VARCHAR(10)  NULL,
    withdrawal_balance_after BIGINT       NULL,
    error_code               VARCHAR(10)  NULL,
    error_message            VARCHAR(200) NULL,
    transferred_at           DATETIME(6)  NOT NULL,
    created_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (transfer_id),
    UNIQUE KEY uk_transfer_txno (transaction_number),
    KEY ix_transfer_wacc (withdrawal_account_id, transferred_at DESC),
    KEY ix_transfer_source (source_type, source_id),
    CONSTRAINT fk_transfer_wacc FOREIGN KEY (withdrawal_account_id) REFERENCES account (account_id),
    CONSTRAINT fk_transfer_dacc FOREIGN KEY (deposit_account_id)    REFERENCES account (account_id),
    CONSTRAINT ck_transfer_amount   CHECK (amount > 0),
    CONSTRAINT ck_transfer_fee      CHECK (fee >= 0),
    CONSTRAINT ck_transfer_selfsend CHECK (withdrawal_account_id <> deposit_account_id)
) ENGINE=InnoDB COMMENT='이체 거래 (즉시·예약·자동 3종의 단일 수렴점)';

CREATE TABLE ledger_entry (
    ledger_entry_id     BIGINT      NOT NULL AUTO_INCREMENT,
    account_id          BIGINT      NOT NULL,
    transfer_id         BIGINT      NULL COMMENT '이체 외 기표(상품가입 초입금 등)는 NULL',
    transaction_number  CHAR(20)    NOT NULL COMMENT '짝이 되는 2행이 동일',
    direction           VARCHAR(10) NOT NULL COMMENT 'DEPOSIT / WITHDRAWAL',
    amount              BIGINT      NOT NULL COMMENT '양수만. 방향은 direction 이 표현',
    balance_after       BIGINT      NOT NULL COMMENT '기표 직후 잔액',
    transaction_type    VARCHAR(32) NOT NULL COMMENT 'IMMEDIATE_TRANSFER / SCHEDULED_TRANSFER / AUTO_TRANSFER / PRODUCT_SUBSCRIPTION / INTEREST / REVERSAL',
    transaction_content VARCHAR(10) NULL COMMENT '통장 표시내용',
    channel             CHAR(2)     NOT NULL COMMENT 'WB / BT',
    reversed            BOOLEAN     NOT NULL DEFAULT FALSE,
    reversal_id         BIGINT      NULL COMMENT '반대기표가 가리키는 원거래',
    occurred_at         DATETIME(6) NOT NULL COMMENT 'RANGE PARTITION KEY',
    PRIMARY KEY (ledger_entry_id, occurred_at),
    KEY ix_le_account_time (account_id, occurred_at DESC),
    KEY ix_le_txno (transaction_number),
    KEY ix_le_account_dir (account_id, direction, occurred_at),
    CONSTRAINT ck_le_amount    CHECK (amount > 0),
    CONSTRAINT ck_le_direction CHECK (direction IN ('DEPOSIT','WITHDRAWAL')),
    -- 거래번호 형식: YYYYMMDD(8) + 채널 영문 2 + 일련 10 = 20자
    CONSTRAINT ck_le_txno      CHECK (transaction_number REGEXP '^[0-9]{8}[A-Z]{2}[0-9]{10}$')
) ENGINE=InnoDB COMMENT='원장 (APPEND-ONLY. UPDATE/DELETE 금지)'
PARTITION BY RANGE COLUMNS (occurred_at) (
    PARTITION p202607 VALUES LESS THAN ('2026-08-01'),
    PARTITION p202608 VALUES LESS THAN ('2026-09-01'),
    PARTITION p202609 VALUES LESS THAN ('2026-10-01'),
    PARTITION p202610 VALUES LESS THAN ('2026-11-01'),
    PARTITION p202611 VALUES LESS THAN ('2026-12-01'),
    PARTITION p202612 VALUES LESS THAN ('2027-01-01'),
    PARTITION pmax    VALUES LESS THAN (MAXVALUE)
);
-- 파티션 테이블 제약: 파티션 키가 모든 UNIQUE 인덱스에 포함돼야 하므로
-- transaction_number 는 UK 가 아닌 일반 인덱스. 유일성은 transfer 가 보장한다.
-- FK 도 선언 불가 -> account_id · transfer_id 정합성은 애플리케이션이 진다.
