package com.shinhan.corebank.transfer.application.port.out;

import java.time.LocalDateTime;

/** 고객이 최근 돈을 보낸 당행 입금계좌 하나와 그 계좌로 마지막 성공 이체한 시각. */
public record RecentDepositAccount(String depositAccountNumber, LocalDateTime lastTransferredAt) {}
