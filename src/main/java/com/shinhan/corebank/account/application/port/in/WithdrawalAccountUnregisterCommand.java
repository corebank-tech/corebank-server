package com.shinhan.corebank.account.application.port.in;

public record WithdrawalAccountUnregisterCommand(
        Long customerId,
        Long accountId
) {
}