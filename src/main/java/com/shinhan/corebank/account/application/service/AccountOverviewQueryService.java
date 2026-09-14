package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.AccountGroupCode;
import com.shinhan.corebank.account.application.port.in.AccountOverviewQueryUseCase;
import com.shinhan.corebank.account.application.port.in.AccountOverviewResult;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.product.application.port.in.ProductQueryUseCase;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountOverviewQueryService implements AccountOverviewQueryUseCase {

    private static final String DEFAULT_DEMAND_DEPOSIT_NAME = "입출금통장";

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final AccountPersistencePort accountPersistencePort;
    private final ProductQueryUseCase productQueryUseCase;
    private final Clock clock;

    @Override
    public AccountOverviewResult getOverview(Long customerId) {
        List<Account> accounts = accountPersistencePort.findAllByCustomerId(customerId).stream()
                .filter(account -> account.getStatus() != AccountStatus.CLOSED)
                .toList();

        OffsetDateTime asOf = OffsetDateTime.ofInstant(clock.instant(), KOREA_ZONE);

        if (accounts.isEmpty()) {
            return new AccountOverviewResult(asOf, 0L, List.of());
        }

        long totalAssets = accounts.stream().mapToLong(Account::getBalance).sum();

        Map<Long, String> productNames = loadProductNames(accounts);

        List<AccountOverviewResult.Group> items = Arrays.stream(AccountGroupCode.values())
                .map(groupCode -> createGroup(groupCode, accounts, productNames))
                .filter(group -> !group.accounts().isEmpty())
                .toList();

        return new AccountOverviewResult(asOf, totalAssets, items);
    }

    private Map<Long, String> loadProductNames(List<Account> accounts) {
        Set<Long> productIds = accounts.stream()
                .filter(account -> account.getAccountType() != AccountType.DEMAND_DEPOSIT)
                .map(Account::getProductId)
                .collect(Collectors.toSet());

        if (productIds.isEmpty()) {
            return Map.of();
        }

        return productQueryUseCase.findProductNames(productIds);
    }

    private AccountOverviewResult.Group createGroup(
            AccountGroupCode groupCode, List<Account> accounts, Map<Long, String> productNames) {
        List<AccountOverviewResult.AccountItem> accountItems = accounts.stream()
                .filter(account -> resolveGroupCode(account.getAccountType()) == groupCode)
                .sorted(accountDisplayOrderComparator())
                .map(account -> toAccountItem(account, productNames))
                .toList();

        long groupTotalBalance = accountItems.stream()
                .mapToLong(AccountOverviewResult.AccountItem::balance)
                .sum();

        return new AccountOverviewResult.Group(groupCode, groupCode.getGroupName(), groupTotalBalance, accountItems);
    }

    private AccountGroupCode resolveGroupCode(AccountType accountType) {
        return switch (accountType) {
            case DEMAND_DEPOSIT -> AccountGroupCode.DEMAND_DEPOSIT;

            case TIME_DEPOSIT, INSTALLMENT_SAVINGS -> AccountGroupCode.DEPOSIT_SAVINGS;
        };
    }

    private AccountOverviewResult.AccountItem toAccountItem(Account account, Map<Long, String> productNames) {

        String alias = resolveAlias(account);
        String baseAccountName = resolveBaseAccountName(account, productNames);
        String accountName = alias != null ? alias : baseAccountName;

        return new AccountOverviewResult.AccountItem(
                account.getAccountId(),
                accountName,
                alias,
                baseAccountName,
                account.getAccountNumber(),
                account.getAccountType(),
                account.getBalance(),
                account.getStatus(),
                account.getOpenedDate().toLocalDate(),
                account.getLastTransactionAt(),
                account.getMaturityDate(),
                account.isWithdrawalRegistered(),
                isTransferEnabled(account));
    }

    private String resolveAlias(Account account) {
        if (account.getAlias() == null || account.getAlias().isBlank()) {
            return null;
        }

        return account.getAlias();
    }

    private String resolveBaseAccountName(Account account, Map<Long, String> productNames) {

        if (account.getAccountType() == AccountType.DEMAND_DEPOSIT) {
            return DEFAULT_DEMAND_DEPOSIT_NAME;
        }

        return productNames.get(account.getProductId());
    }

    private boolean isTransferEnabled(Account account) {
        return account.getAccountType() == AccountType.DEMAND_DEPOSIT
                && account.getStatus() == AccountStatus.ACTIVE
                && account.isWithdrawalRegistered();
    }

    private Comparator<Account> accountDisplayOrderComparator() {
        return Comparator.comparing(Account::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Account::getOpenedDate)
                .thenComparing(Account::getAccountId);
    }
}
