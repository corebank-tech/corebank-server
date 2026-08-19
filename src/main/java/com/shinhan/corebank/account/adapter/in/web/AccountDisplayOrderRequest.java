package com.shinhan.corebank.account.adapter.in.web;

import java.util.List;

public record AccountDisplayOrderRequest(
        List<Long> accountIds
) {
}