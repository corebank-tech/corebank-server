package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.api.MaturityAccountQueryUseCase;
import com.shinhan.corebank.account.api.MaturityAccountView;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.application.port.out.MaturityAccountSnapshot;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaturityAccountQueryService implements MaturityAccountQueryUseCase {

    private final AccountPersistencePort accountPersistencePort;

    @Override
    @Transactional(readOnly = true)
    public MaturityAccountView requireMatured(Long accountId, Long customerId, LocalDate actualDate) {

        if (actualDate == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        MaturityAccountSnapshot account = accountPersistencePort
                .findMaturityAccountSnapshot(accountId, customerId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN));

        if (account.accountType() == null || !account.accountType().isMaturityEligible()) {
            throw new BusinessException(AccountErrorCode.MATURITY_UNSUPPORTED_ACCOUNT_TYPE);
        }

        if (account.status() != AccountStatus.MATURED) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_STATUS);
        }

        if (account.maturityDate() == null || account.maturityDate().isAfter(actualDate)) {
            throw new BusinessException(AccountErrorCode.MATURITY_DATE_NOT_REACHED);
        }

        return new MaturityAccountView(
                account.accountId(),
                account.productId(),
                account.maturityDate(),
                account.accountType() == AccountType.TIME_DEPOSIT);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireWithdrawalDestination(Long accountId, Long customerId) {

        MaturityAccountSnapshot account = accountPersistencePort
                .findMaturityAccountSnapshot(accountId, customerId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN));

        validateWithdrawalDestination(account.accountType(), account.status(), account.withdrawalRegistered());
    }

    @Override
    @Transactional
    public void requireWithdrawalDestinationForUpdate(Long accountId, Long customerId) {

        Account account = accountPersistencePort
                .findByAccountIdAndCustomerIdForUpdate(accountId, customerId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN));

        validateWithdrawalDestination(account.getAccountType(), account.getStatus(), account.isWithdrawalRegistered());
    }

    private static void validateWithdrawalDestination(
            AccountType accountType, AccountStatus status, boolean withdrawalRegistered) {

        if (accountType != AccountType.DEMAND_DEPOSIT || status != AccountStatus.ACTIVE || !withdrawalRegistered) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN);
        }
    }
}
