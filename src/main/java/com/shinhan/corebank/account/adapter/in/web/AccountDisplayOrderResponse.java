package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.AccountDisplayOrderResult;

import java.util.List;

public record AccountDisplayOrderResponse(
        List<Long> accountIds
) {

    public AccountDisplayOrderResponse {
        accountIds = List.copyOf(accountIds);
    }

    public static AccountDisplayOrderResponse from(
            AccountDisplayOrderResult result
    ) {
        return new AccountDisplayOrderResponse(
                result.accountIds()
        );
    }
}