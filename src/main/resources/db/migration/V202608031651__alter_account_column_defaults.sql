ALTER TABLE account
    MODIFY COLUMN status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE'
        COMMENT '계좌 상태: ACTIVE, SUSPENDED, CLOSED',

    MODIFY COLUMN created_at DATETIME(6) NOT NULL
        COMMENT '행 생성 일시',

    MODIFY COLUMN updated_at DATETIME(6) NOT NULL
        COMMENT '행 최종 수정 일시',

    MODIFY COLUMN opened_date DATETIME(6) NOT NULL
        COMMENT '계좌 개설일',

    MODIFY COLUMN closed_date DATETIME(6) NOT NULL
        COMMENT '계좌 해지일';