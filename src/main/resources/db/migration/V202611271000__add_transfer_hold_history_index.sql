-- #1022 고객 이체결과조회에서 출금계좌·보류시각 기준으로 transfer_hold를 범위 조회한다.
ALTER TABLE transfer_hold
    ADD KEY ix_transfer_hold_withdrawal_held (withdrawal_account_id, held_at);
