-- PH-16 #847: 연결 예약이체 조회의 잠금 범위 축소
CREATE INDEX ix_sched_payee_status
    ON scheduled_transfer (payee_account_number, status);
