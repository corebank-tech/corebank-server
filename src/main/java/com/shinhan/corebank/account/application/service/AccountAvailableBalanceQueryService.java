package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.AvailableBalanceQueryUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountAvailableBalanceQueryService implements AvailableBalanceQueryUseCase {

    private final AccountPersistencePort accountPersistencePort;

    @Override
    public AvailableBalanceSnapshot getForTransfer(Long accountId, Long customerId) {

        Account account = accountPersistencePort
                .findByAccountIdAndCustomerId(accountId, customerId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN));

        return new AvailableBalanceSnapshot(account.getStatus() == AccountStatus.ACTIVE, account.getAvailableBalance());
    }
}
