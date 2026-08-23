package com.shinhan.corebank.account.application.port.in;

import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public record AccountOverviewResult(
        OffsetDateTime asOf,
        long totalAssets,
        List<Group> items
) {

    public record Group(
            AccountGroupCode groupCode,
            String groupName,
            long groupTotalBalance,
            List<AccountItem> accounts
    ) {
    }

    public record AccountItem(
            Long accountId,
            String accountName,
            String accountNumber,
            AccountType accountType,
            long balance,
            AccountStatus status,
            LocalDate openedDate,
            LocalDateTime lastTransactionAt,
            LocalDate maturityDate,
            boolean withdrawalRegistered,
            boolean transferEnabled
    ) {
    }
}