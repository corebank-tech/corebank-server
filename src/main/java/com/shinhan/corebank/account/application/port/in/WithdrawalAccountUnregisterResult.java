package com.shinhan.corebank.account.application.port.in;

public record WithdrawalAccountUnregisterResult(
        Long accountId,
        boolean withdrawalAccountRegistered
) {
}
