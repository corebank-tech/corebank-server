package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.WithdrawalAccountUnregisterResult;

public record WithdrawalAccountUnregisterResponse(
        Long accountId,
        boolean withdrawalAccountRegistered
) {

    public static WithdrawalAccountUnregisterResponse from(
            WithdrawalAccountUnregisterResult result
    ) {
        return new WithdrawalAccountUnregisterResponse(
                result.accountId(),
                result.withdrawalAccountRegistered()
        );
    }
}
