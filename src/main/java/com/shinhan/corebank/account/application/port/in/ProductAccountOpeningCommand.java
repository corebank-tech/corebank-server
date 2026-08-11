package com.shinhan.corebank.account.application.port.in;

import com.shinhan.corebank.account.domain.AccountType;

import java.time.LocalDate;

public record ProductAccountOpeningCommand(
        Long customerId,
        Long productId,
        AccountType accountType,
        String passwordHash,
        LocalDate maturityDate
) {
}