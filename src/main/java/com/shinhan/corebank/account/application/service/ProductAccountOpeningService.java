package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.AccountOpeningResult;
import com.shinhan.corebank.account.application.port.in.IssueAccountNumberUseCase;
import com.shinhan.corebank.account.application.port.in.ProductAccountOpeningCommand;
import com.shinhan.corebank.account.application.port.in.ProductAccountOpeningUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductAccountOpeningService
        implements ProductAccountOpeningUseCase {

    private final IssueAccountNumberUseCase issueAccountNumberUseCase;
    private final AccountPersistencePort accountPersistencePort;
    private final Clock clock;

    @Override
    @Transactional
    public AccountOpeningResult open(
            ProductAccountOpeningCommand command
    ) {
        validateProductAccountType(command.accountType());

        String accountNumber =
                issueAccountNumberUseCase.issue(
                        command.accountType(),
                        command.productId()
                );

        LocalDateTime openedDate =
                LocalDateTime.now(clock);

        Account account = Account.open(
                accountNumber,
                command.customerId(),
                command.productId(),
                command.accountType(),
                command.newAccountPasswordHash(),
                openedDate,
                command.maturityDate()
        );

        Account savedAccount =
                accountPersistencePort.save(account);

        return new AccountOpeningResult(
                savedAccount.getAccountId(),
                savedAccount.getAccountNumber()
        );
    }

    private void validateProductAccountType(
            AccountType accountType
    ) {
        if (accountType != AccountType.TIME_DEPOSIT
                && accountType
                != AccountType.INSTALLMENT_SAVINGS) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT
            );
        }
    }
}