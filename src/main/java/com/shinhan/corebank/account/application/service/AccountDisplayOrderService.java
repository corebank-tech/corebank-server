package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.AccountDisplayOrderCommand;
import com.shinhan.corebank.account.application.port.in.AccountDisplayOrderResult;
import com.shinhan.corebank.account.application.port.in.AccountDisplayOrderUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountDisplayOrderService
        implements AccountDisplayOrderUseCase {

    private final AccountPersistencePort accountPersistencePort;

    @Override
    public AccountDisplayOrderResult saveDisplayOrder(
            AccountDisplayOrderCommand command
    ) {
        List<Account> accounts =
                accountPersistencePort.findAllByCustomerId(
                        command.customerId()
                );

        validateDisplayOrderRequest(
                command.accountIds(),
                accounts
        );

        Map<Long, Account> accountById =
                createAccountMap(accounts);

        for (int index = 0;
             index < command.accountIds().size();
             index++) {

            Long accountId =
                    command.accountIds().get(index);

            Account account =
                    accountById.get(accountId);

            account.changeDisplayOrder(index + 1);

            accountPersistencePort.save(account);
        }

        return new AccountDisplayOrderResult(
                command.accountIds()
        );
    }

    @Override
    public AccountDisplayOrderResult resetDisplayOrder(
            Long customerId
    ) {
        List<Account> accounts =
                accountPersistencePort.findAllByCustomerId(
                        customerId
                );

        for (Account account : accounts) {
            if (account.getDisplayOrder() == null) {
                continue;
            }
            account.resetDisplayOrder();

            accountPersistencePort.save(account);
        }

        List<Long> accountIds =
                accounts.stream()
                        .sorted(defaultOrderComparator())
                        .map(Account::getAccountId)
                        .toList();

        return new AccountDisplayOrderResult(
                accountIds
        );
    }

    private void validateDisplayOrderRequest(
            List<Long> requestedAccountIds,
            List<Account> accounts
    ) {
        Set<Long> requestedIdSet =
                new HashSet<>(requestedAccountIds);

        if (requestedIdSet.size()
                != requestedAccountIds.size()) {
            throw new BusinessException(
                    AccountErrorCode.INVALID_DISPLAY_ORDER
            );
        }

        Set<Long> ownedAccountIds =
                accounts.stream()
                        .map(Account::getAccountId)
                        .collect(
                                java.util.stream.Collectors.toSet()
                        );

        boolean containsUnknownAccount =
                requestedIdSet.stream()
                        .anyMatch(
                                accountId ->
                                        !ownedAccountIds.contains(
                                                accountId
                                        )
                        );

        if (containsUnknownAccount) {
            throw new BusinessException(
                    AccountErrorCode
                            .ACCOUNT_NOT_FOUND_OR_FORBIDDEN
            );
        }

        if (!requestedIdSet.equals(ownedAccountIds)) {
            throw new BusinessException(
                    AccountErrorCode.INVALID_DISPLAY_ORDER
            );
        }
    }

    private Map<Long, Account> createAccountMap(
            List<Account> accounts
    ) {
        Map<Long, Account> accountById =
                new HashMap<>();

        for (Account account : accounts) {
            accountById.put(
                    account.getAccountId(),
                    account
            );
        }

        return accountById;
    }

    private Comparator<Account> defaultOrderComparator() {
        return Comparator
                .comparing(Account::getOpenedDate)
                .thenComparing(Account::getAccountId);
    }
}