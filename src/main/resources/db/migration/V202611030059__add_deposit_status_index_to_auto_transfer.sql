-- PH-16 #847: 연결 자동이체 조회의 잠금 범위 축소
CREATE INDEX ix_auto_deposit_status
    ON auto_transfer (deposit_account_number, status);
