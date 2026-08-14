package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.AccountAliasUseCase;
import com.shinhan.corebank.account.application.port.in.AccountAliasResult;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountAliasService implements AccountAliasUseCase {

    private final AccountPersistencePort
            accountPersistencePort;

    @Override
    public AccountAliasResult changeAlias(
            Long customerId,
            Long accountId,
            String alias
    ) {
        Account account =
                getOwnedAccount(
                        accountId,
                        customerId
                );

        account.changeAlias(alias);

        Account savedAccount =
                accountPersistencePort.save(account);

        return new AccountAliasResult(
                savedAccount.getAccountId(),
                savedAccount.getAlias()
        );
    }

    @Override
    public void deleteAlias(
            Long customerId,
            Long accountId
    ) {
        Account account =
                getOwnedAccount(
                        accountId,
                        customerId
                );

        account.removeAlias();

        accountPersistencePort.save(account);
    }

    private Account getOwnedAccount(
            Long accountId,
            Long customerId
    ) {
        return accountPersistencePort
                .findByAccountIdAndCustomerId(
                        accountId,
                        customerId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                AccountErrorCode
                                        .ACCOUNT_NOT_FOUND_OR_FORBIDDEN
                        )
                );
    }
}