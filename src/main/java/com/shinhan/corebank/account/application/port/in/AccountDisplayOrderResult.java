package com.shinhan.corebank.account.application.port.in;

import java.util.List;

public record AccountDisplayOrderResult(
        List<Long> accountIds
) {

    public AccountDisplayOrderResult {
        accountIds = List.copyOf(accountIds);
    }
}