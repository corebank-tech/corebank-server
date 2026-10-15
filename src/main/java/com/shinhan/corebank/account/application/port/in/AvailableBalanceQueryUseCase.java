package com.shinhan.corebank.account.application.port.in;

public interface AvailableBalanceQueryUseCase {

    AvailableBalanceSnapshot getForTransfer(Long accountId, Long customerId);

    record AvailableBalanceSnapshot(boolean active, long availableBalance) {}
}
