-- ① 일별 계좌잔액 (계좌 × 기준일, 일별 스냅샷)
CREATE TABLE mart_daily_account_balance (
    base_date      DATE                          NOT NULL COMMENT '거래일 기준 마감일 = as-of',
    account_key    CHAR(64) CHARACTER SET ascii  NOT NULL COMMENT 'HMAC-SHA256(account:{account_id}) hex',
    customer_key   CHAR(64) CHARACTER SET ascii  NOT NULL COMMENT 'HMAC-SHA256(customer:{customer_id}) hex',
    account_type   VARCHAR(24)                   NOT NULL COMMENT 'DEMAND_DEPOSIT / TIME_DEPOSIT / INSTALLMENT_SAVINGS',
    product_id     BIGINT                            NULL COMMENT '입출금계좌는 NULL',
    product_group  VARCHAR(12)                       NULL COMMENT 'SAVINGS / DEPOSIT. 입출금계좌는 NULL',
    balance        BIGINT                        NOT NULL COMMENT 'base_date 마감 잔액(원)',
    load_batch_id  BIGINT                        NOT NULL,
    PRIMARY KEY (base_date, account_key),
    KEY ix_mdab_product (base_date, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='일별 계좌잔액 마트';

-- ② 상품 가입·해지 이벤트 (이벤트 1건 = 1행, 추가 전용)
CREATE TABLE mart_product_subscription_event (
    base_date      DATE                          NOT NULL COMMENT '이벤트 거래일',
    event_type     VARCHAR(12)                   NOT NULL COMMENT 'SUBSCRIBE / CLOSE',
    account_key    CHAR(64) CHARACTER SET ascii  NOT NULL,
    customer_key   CHAR(64) CHARACTER SET ascii  NOT NULL,
    product_id     BIGINT                        NOT NULL,
    product_code   VARCHAR(20)                   NOT NULL COMMENT '적재 시점 스냅샷',
    product_name   VARCHAR(100)                  NOT NULL COMMENT '적재 시점 스냅샷',
    product_group  VARCHAR(12)                   NOT NULL,
    load_batch_id  BIGINT                        NOT NULL,
    PRIMARY KEY (base_date, event_type, account_key),
    UNIQUE KEY uk_mpse_account_event (account_key, event_type),
    KEY ix_mpse_product (base_date, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='상품 가입·해지 이벤트 마트';

-- ③ 적재 기록 (기준일 1회 적재 = 1행. asOf 와 대조 결과의 출처)
CREATE TABLE mart_load_batch (
    load_batch_id          BIGINT       NOT NULL AUTO_INCREMENT,
    base_date              DATE         NOT NULL,
    status                 VARCHAR(12)  NOT NULL COMMENT 'RUNNING / COMPLETED / FAILED / REPLACED',
    started_at             DATETIME(6)  NOT NULL,
    completed_at           DATETIME(6)      NULL COMMENT '운영용. API 로 내보내지 않는다',
    balance_row_count      BIGINT           NULL,
    event_row_count        BIGINT           NULL,
    mart_balance_total     BIGINT           NULL COMMENT 'SUM(mart_daily_account_balance.balance)',
    ledger_total           BIGINT           NULL COMMENT '원장 입금 합계 - 출금 합계 (td <= base_date)',
    gl_compare_from_date   DATE             NULL COMMENT '직전 COMPLETED base_date (D′). 첫 적재면 NULL → GL 대조 생략',
    mart_balance_movement  BIGINT           NULL COMMENT 'mart_balance_total(D) - mart_balance_total(D′)',
    gl_deposit_movement    BIGINT           NULL COMMENT 'GL 20100 (대변 합계 - 차변 합계), D′ < trade_date <= D',
    as_of_date             DATE             NULL COMMENT 'COMPLETED 로 커밋할 때 계산한 연속 워터마크(§5-4). API 의 asOf 출처',
    failure_reason         VARCHAR(200)     NULL,
    PRIMARY KEY (load_batch_id),
    KEY ix_mlb_date_status (base_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='정보계 적재 기록';
