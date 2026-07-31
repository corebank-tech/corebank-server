-- ====================================================================
-- V202608010940__create_transfer_ext.sql
-- 자주쓰는계좌·예약이체·자동이체 (P3·P4·P5)

-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
-- ====================================================================

CREATE TABLE favorite_account (
    favorite_account_id    BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id            BIGINT      NOT NULL,
    deposit_account_number CHAR(12)    NOT NULL,
    payee_name             VARCHAR(50) NOT NULL COMMENT '등록 시점 예금주 스냅샷',
    alias                  VARCHAR(24) NULL COMMENT '미지정 시 예금주명 (FAV0001)',
    registered_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (favorite_account_id),
    -- FAV0301 중복 등록 방지
    UNIQUE KEY uk_fav (customer_id, deposit_account_number),
    CONSTRAINT fk_fav_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id)
) ENGINE=InnoDB COMMENT='자주 쓰는 계좌 (최대 20건은 앱 검증)';

CREATE TABLE scheduled_transfer (
    scheduled_transfer_id   BIGINT       NOT NULL AUTO_INCREMENT,
    customer_id             BIGINT       NOT NULL,
    withdrawal_account_id   BIGINT       NOT NULL,
    payee_bank_code         CHAR(3)      NOT NULL COMMENT '당행 상수. bank 테이블 없음',
    payee_account_number    CHAR(12)     NOT NULL,
    payee_name              VARCHAR(50)  NOT NULL,
    amount                  BIGINT       NOT NULL,
    scheduled_date          DATE         NOT NULL COMMENT '익일~1년 이내 (SCD0001)',
    my_passbook_memo        VARCHAR(10)  NULL,
    recipient_passbook_memo VARCHAR(10)  NULL,
    status                  VARCHAR(12)  NOT NULL COMMENT 'WAITING / PROCESSING / SUCCESS / FAILED / CANCELED',
    transaction_number      CHAR(20)     NULL,
    registered_at           DATETIME(6)  NOT NULL,
    executed_at             DATETIME(6)  NULL,
    canceled_at             DATETIME(6)  NULL,
    failure_reason          VARCHAR(200) NULL,
    -- WAITING 건에만 유일성을 적용한다. 취소·완료 후 같은 조건으로 재예약할 수 있어야 한다.
    active_dup_key VARCHAR(80) GENERATED ALWAYS AS (
        CASE WHEN status = 'WAITING'
             THEN CONCAT_WS('|', customer_id, withdrawal_account_id, payee_account_number, amount, scheduled_date)
        END
    ) STORED,
    PRIMARY KEY (scheduled_transfer_id),
    UNIQUE KEY uk_sched_active_dup (active_dup_key),
    KEY ix_sched_batch (status, scheduled_date),
    CONSTRAINT fk_sched_customer FOREIGN KEY (customer_id)           REFERENCES customer (customer_id),
    CONSTRAINT fk_sched_account  FOREIGN KEY (withdrawal_account_id) REFERENCES account (account_id),
    CONSTRAINT ck_sched_amount CHECK (amount > 0)
) ENGINE=InnoDB COMMENT='예약이체';

CREATE TABLE auto_transfer (
    auto_transfer_id        BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id             BIGINT      NOT NULL,
    withdrawal_account_id   BIGINT      NOT NULL,
    deposit_account_number  CHAR(12)    NOT NULL,
    payee_name              VARCHAR(50) NOT NULL,
    amount                  BIGINT      NOT NULL,
    cycle_months            TINYINT     NOT NULL COMMENT '1 / 3 / 6',
    transfer_day            TINYINT     NOT NULL COMMENT '1~31 (AUT0001)',
    start_date              DATE        NOT NULL,
    end_date                DATE        NOT NULL COMMENT '60개월 이내 (AUT0002)',
    next_execution_date     DATE        NOT NULL,
    my_passbook_memo        VARCHAR(10) NULL,
    recipient_passbook_memo VARCHAR(10) NULL,
    status                  VARCHAR(12) NOT NULL COMMENT 'NORMAL / EXPIRED / TERMINATED (AutoTransferStatus)',
    registered_at           DATETIME(6) NOT NULL,
    terminated_at           DATETIME(6) NULL,
    updated_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    -- NORMAL 건에만 유일성을 적용한다. 해지 후 재등록이 가능해야 하고
    -- 해지 이력이 여러 건 쌓여도 충돌하면 안 된다.
    active_dup_key VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN status = 'NORMAL'
             THEN CONCAT_WS('|', customer_id, withdrawal_account_id, deposit_account_number, transfer_day)
        END
    ) STORED,
    PRIMARY KEY (auto_transfer_id),
    UNIQUE KEY uk_auto_active_dup (active_dup_key),
    KEY ix_auto_batch (status, next_execution_date),
    CONSTRAINT fk_auto_customer FOREIGN KEY (customer_id)           REFERENCES customer (customer_id),
    CONSTRAINT fk_auto_account  FOREIGN KEY (withdrawal_account_id) REFERENCES account (account_id),
    CONSTRAINT ck_auto_cycle  CHECK (cycle_months IN (1, 3, 6)),
    CONSTRAINT ck_auto_day    CHECK (transfer_day BETWEEN 1 AND 31),
    CONSTRAINT ck_auto_period CHECK (end_date >= start_date),
    CONSTRAINT ck_auto_amount CHECK (amount > 0)
) ENGINE=InnoDB COMMENT='자동이체 등록';

CREATE TABLE auto_transfer_execution (
    execution_id       BIGINT       NOT NULL AUTO_INCREMENT,
    auto_transfer_id   BIGINT       NOT NULL,
    execution_date     DATE         NOT NULL,
    amount             BIGINT       NOT NULL,
    status             VARCHAR(12)  NOT NULL COMMENT 'SUCCESS / ERROR / PROCESSING (ProcessResultStatus)',
    transaction_number CHAR(20)     NULL,
    failure_reason     VARCHAR(200) NULL,
    executed_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (execution_id),
    -- 배치 재실행 시 중복 기표 방지
    UNIQUE KEY uk_ate_dup (auto_transfer_id, execution_date),
    CONSTRAINT fk_ate_auto FOREIGN KEY (auto_transfer_id) REFERENCES auto_transfer (auto_transfer_id)
) ENGINE=InnoDB COMMENT='자동이체 회차 실행결과';
