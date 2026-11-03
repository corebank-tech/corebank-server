package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.api.EarlyTerminationAccountQueryUseCase;
import com.shinhan.corebank.account.api.EarlyTerminationAccountView;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.application.port.out.MaturityAccountSnapshot;
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
public class EarlyTerminationAccountQueryService implements EarlyTerminationAccountQueryUseCase {

    private final AccountPersistencePort accountPersistencePort;

    @Override
    @Transactional(readOnly = true)
    public EarlyTerminationAccountView requireEarlyTerminable(
            Long accountId, Long customerId, LocalDate terminationDate) {
        if (terminationDate == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        MaturityAccountSnapshot account = accountPersistencePort
                .findMaturityAccountSnapshot(accountId, customerId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN));

        if (account.accountType() == null
                || !account.accountType().isMaturityEligible()
                || account.status() != AccountStatus.ACTIVE
                || account.maturityDate() == null
                || !terminationDate.isBefore(account.maturityDate())) {
            throw new BusinessException(AccountErrorCode.EARLY_TERMINATION_NOT_ALLOWED);
        }

        return new EarlyTerminationAccountView(
                account.accountId(),
                account.productId(),
                account.maturityDate(),
                account.accountType() == AccountType.TIME_DEPOSIT);
    }
}
