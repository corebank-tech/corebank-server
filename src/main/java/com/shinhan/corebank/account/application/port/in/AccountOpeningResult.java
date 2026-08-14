package com.shinhan.corebank.account.application.port.in;

public record AccountOpeningResult(
        Long accountId,
        String accountNumber
) {
}