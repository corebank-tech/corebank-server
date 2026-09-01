package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.IssueAccountNumberUseCase;
import com.shinhan.corebank.account.application.port.out.AccountNumberSequencePort;
import com.shinhan.corebank.account.domain.AccountNumberPolicy;
import com.shinhan.corebank.account.domain.AccountNumberSequence;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountNumberIssuanceService implements IssueAccountNumberUseCase {
    private final AccountNumberSequencePort sequencePort;

    @Override
    @Transactional
    public String issue(AccountType accountType, Long productId) {
        validateRequest(accountType, productId);

        AccountNumberSequence sequence = sequencePort
                .findForUpdate(AccountNumberPolicy.BANK_CODE, accountType, productId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NUMBER_SEQUENCE_NOT_FOUND));
        if (sequence.isExhausted()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NUMBER_SEQUENCE_EXHAUSTED);
        }
        String accountNumber = sequence.issueNext();
        sequencePort.update(sequence);
        return accountNumber;
    }

    private void validateRequest(AccountType accountType, Long productId) {
        if (accountType == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        boolean demandDeposit = accountType == AccountType.DEMAND_DEPOSIT;
        boolean invalidCombination =
                (demandDeposit && productId != null) || (!demandDeposit && (productId == null || productId <= 0));

        if (invalidCombination) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
