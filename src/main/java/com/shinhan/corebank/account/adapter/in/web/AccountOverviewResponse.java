package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.AccountGroupCode;
import com.shinhan.corebank.account.application.port.in.AccountOverviewResult;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public record AccountOverviewResponse(
        OffsetDateTime asOf,
        long totalAssets,
        List<GroupResponse> items
) {

    public static AccountOverviewResponse from(
            AccountOverviewResult result
    ) {
        return new AccountOverviewResponse(
                result.asOf(),
                result.totalAssets(),
                result.items().stream()
                        .map(GroupResponse::from)
                        .toList()
        );
    }

    public record GroupResponse(
            AccountGroupCode groupCode,
            String groupName,
            long groupTotalBalance,
            List<AccountItemResponse> accounts
    ) {

        private static GroupResponse from(
                AccountOverviewResult.Group group
        ) {
            return new GroupResponse(
                    group.groupCode(),
                    group.groupName(),
                    group.groupTotalBalance(),
                    group.accounts().stream()
                            .map(AccountItemResponse::from)
                            .toList()
            );
        }
    }

    public record AccountItemResponse(
            Long accountId,
            String accountName,
            String accountNumber,
            AccountType accountType,
            long balance,
            AccountStatus status,
            LocalDate openedDate,
            LocalDateTime lastTransactionAt,
            LocalDate maturityDate,
            boolean transferEnabled
    ) {

        private static AccountItemResponse from(
                AccountOverviewResult.AccountItem account
        ) {
            return new AccountItemResponse(
                    account.accountId(),
                    account.accountName(),
                    account.accountNumber(),
                    account.accountType(),
                    account.balance(),
                    account.status(),
                    account.openedDate(),
                    account.lastTransactionAt(),
                    account.maturityDate(),
                    account.transferEnabled()
            );
        }
    }
}