-- PH-16 #847: 해지 계좌 연결 이체 조회의 잠금 범위 축소

CREATE INDEX ix_auto_deposit_status
    ON auto_transfer (deposit_account_number, status);

CREATE INDEX ix_sched_payee_status
    ON scheduled_transfer (payee_account_number, status);