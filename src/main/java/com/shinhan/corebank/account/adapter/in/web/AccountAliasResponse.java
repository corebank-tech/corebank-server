package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.AccountAliasResult;

public record AccountAliasResponse(
        Long accountId,
        String alias
) {

    public static AccountAliasResponse from(
            AccountAliasResult result
    ) {
        return new AccountAliasResponse(
                result.accountId(),
                result.alias()
        );
    }
}
