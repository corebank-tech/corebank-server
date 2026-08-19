package com.shinhan.corebank.account.application.port.in;

import java.util.List;

public record AccountDisplayOrderCommand(
        Long customerId,
        List<Long> accountIds
) {
}