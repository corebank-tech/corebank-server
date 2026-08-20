package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.WithdrawalAccountRegisterResult;

import java.time.OffsetDateTime;

public record WithdrawalAccountRegisterResponse(
        Long accountId,
        OffsetDateTime registeredAt
) {

    public static WithdrawalAccountRegisterResponse from(
            WithdrawalAccountRegisterResult result
    ) {
        return new WithdrawalAccountRegisterResponse(
                result.accountId(),
                result.registeredAt()
        );
    }
}