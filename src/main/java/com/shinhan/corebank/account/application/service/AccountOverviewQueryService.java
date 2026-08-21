package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.AccountGroupCode;
import com.shinhan.corebank.account.application.port.in.AccountOverviewQueryUseCase;
import com.shinhan.corebank.account.application.port.in.AccountOverviewResult;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.product.application.port.in.ProductQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountOverviewQueryService
        implements AccountOverviewQueryUseCase {

    private static final String DEFAULT_DEMAND_DEPOSIT_NAME =
            "입출금통장";

    private static final ZoneId KOREA_ZONE =
            ZoneId.of("Asia/Seoul");

    private final AccountPersistencePort accountPersistencePort;
    private final ProductQueryUseCase productQueryUseCase;
    private final Clock clock;

    @Override
    public AccountOverviewResult getOverview(Long customerId) {
        List<Account> accounts =
                accountPersistencePort.findAllByCustomerId(
                                customerId
                        )
                        .stream()
                        .filter(account ->
                                account.getStatus()
                                        != AccountStatus.CLOSED
                        )
                        .toList();

        OffsetDateTime asOf =
                OffsetDateTime.ofInstant(
                        clock.instant(),
                        KOREA_ZONE
                );

        if (accounts.isEmpty()) {
            return new AccountOverviewResult(
                    asOf,
                    0L,
                    List.of()
            );
        }

        long totalAssets =
                accounts.stream()
                        .mapToLong(Account::getBalance)
                        .sum();

        Map<Long, String> productNameCache =
                new HashMap<>();

        List<AccountOverviewResult.Group> items =
                Arrays.stream(AccountGroupCode.values())
                        .map(groupCode ->
                                createGroup(
                                        groupCode,
                                        accounts,
                                        productNameCache
                                )
                        )
                        .filter(group ->
                                !group.accounts().isEmpty()
                        )
                        .toList();

        return new AccountOverviewResult(
                asOf,
                totalAssets,
                items
        );
    }

    private AccountOverviewResult.Group createGroup(
            AccountGroupCode groupCode,
            List<Account> accounts,
            Map<Long, String> productNameCache
    ) {
        List<AccountOverviewResult.AccountItem> accountItems =
                accounts.stream()
                        .filter(account ->
                                resolveGroupCode(
                                        account.getAccountType()
                                ) == groupCode
                        )
                        .sorted(accountDisplayOrderComparator())
                        .map(account ->
                                toAccountItem(
                                        account,
                                        productNameCache
                                )
                        )
                        .toList();

        long groupTotalBalance =
                accountItems.stream()
                        .mapToLong(
                                AccountOverviewResult.AccountItem::balance
                        )
                        .sum();

        return new AccountOverviewResult.Group(
                groupCode,
                groupCode.getGroupName(),
                groupTotalBalance,
                accountItems
        );
    }

    private AccountGroupCode resolveGroupCode(
            AccountType accountType
    ) {
        return switch (accountType) {
            case DEMAND_DEPOSIT -> AccountGroupCode.DEMAND_DEPOSIT;

            case TIME_DEPOSIT,
                 INSTALLMENT_SAVINGS -> AccountGroupCode.DEPOSIT_SAVINGS;
        };
    }

    private AccountOverviewResult.AccountItem toAccountItem(
            Account account,
            Map<Long, String> productNameCache
    ) {
        return new AccountOverviewResult.AccountItem(
                account.getAccountId(),
                resolveAccountName(
                        account,
                        productNameCache
                ),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getBalance(),
                resolveAvailableBalance(account),
                account.getStatus(),
                account.getOpenedDate().toLocalDate(),
                account.getLastTransactionAt(),
                account.getMaturityDate(),
                isTransferEnabled(account)
        );
    }

    private String resolveAccountName(
            Account account,
            Map<Long, String> productNameCache
    ) {
        if (account.getAlias() != null
                && !account.getAlias().isBlank()) {
            return account.getAlias();
        }

        if (account.getAccountType()
                == AccountType.DEMAND_DEPOSIT) {
            return DEFAULT_DEMAND_DEPOSIT_NAME;
        }

        return productNameCache.computeIfAbsent(
                account.getProductId(),
                this::getProductName
        );
    }

    private String getProductName(Long productId) {
        return productQueryUseCase
                .getDetail(productId)
                .getProduct()
                .getProductName();
    }

    private long resolveAvailableBalance(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            return 0L;
        }

        if (account.isPasswordLocked()) {
            return 0L;
        }

        return account.getBalance();
    }

    private boolean isTransferEnabled(Account account) {
        return account.getAccountType()
                == AccountType.DEMAND_DEPOSIT
                && account.getStatus()
                == AccountStatus.ACTIVE
                && account.isWithdrawalRegistered();
    }

    private Comparator<Account> accountDisplayOrderComparator() {
        return Comparator
                .comparing(
                        Account::getDisplayOrder,
                        Comparator.nullsLast(
                                Integer::compareTo
                        )
                )
                .thenComparing(Account::getOpenedDate)
                .thenComparing(Account::getAccountId);
    }
}
