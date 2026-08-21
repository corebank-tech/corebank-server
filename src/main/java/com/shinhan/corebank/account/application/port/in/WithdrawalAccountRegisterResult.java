package com.shinhan.corebank.account.application.port.in;

import java.time.OffsetDateTime;

public record WithdrawalAccountRegisterResult(
        Long accountId,
        OffsetDateTime registeredAt
) {
}