package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.AccountOpeningResult;
import com.shinhan.corebank.account.application.port.in.DemandDepositAccountOpeningCommand;
import com.shinhan.corebank.account.application.port.in.DemandDepositAccountOpeningUseCase;
import com.shinhan.corebank.account.application.port.in.IssueAccountNumberUseCase;
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
public class DemandDepositAccountOpeningService implements DemandDepositAccountOpeningUseCase {

    private final IssueAccountNumberUseCase issueAccountNumberUseCase;
    private final AccountPersistencePort accountPersistencePort;
    private final Clock clock;

    @Override
    @Transactional
    public AccountOpeningResult open(
            DemandDepositAccountOpeningCommand command
    ) {
        if (command == null) {
            throw new BusinessException(
                    CommonErrorCode.REQUIRED_FIELD_MISSING
            );
        }

        String accountNumber =
                issueAccountNumberUseCase.issue(
                        AccountType.DEMAND_DEPOSIT,
                        null
                );

        LocalDateTime openedDate =
                LocalDateTime.now(clock);

        Account account = Account.open(
                accountNumber,
                command.customerId(),
                null,
                AccountType.DEMAND_DEPOSIT,
                command.newAccountPasswordHash(),
                openedDate,
                null
        );

        Account savedAccount =
                accountPersistencePort.save(account);

        return new AccountOpeningResult(
                savedAccount.getAccountId(),
                savedAccount.getAccountNumber()
        );
    }
}
