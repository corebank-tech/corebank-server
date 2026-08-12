package com.shinhan.corebank.account.application.port.in;

public record DemandDepositAccountOpeningCommand(
        Long customerId,
        String passwordHash
) {
}