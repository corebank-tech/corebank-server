package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.api.ExistingAccountRegistration;
import com.shinhan.corebank.account.api.RegisterExistingAccountsCommand;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import org.springframework.stereotype.Service;

// 기존 은행 원장의 계좌를 검증해 신규 고객 계좌로 등록한다.
@Service
public class ExistingAccountRegistrationService
        implements ExistingAccountRegistration {

    private final AccountPersistencePort accountPersistencePort;

    public ExistingAccountRegistrationService(
            AccountPersistencePort accountPersistencePort
    ) {
        this.accountPersistencePort = accountPersistencePort;
    }

    @Override
    public void registerAll(RegisterExistingAccountsCommand command) {
        if (command.customerId() == null || command.customerId() <= 0) {
            throw new IllegalArgumentException("유효한 고객 ID가 필요합니다.");
        }
        if (command.accounts().isEmpty()) {
            throw new IllegalArgumentException("등록할 기존 계좌가 없습니다.");
        }

        for (RegisterExistingAccountsCommand.AccountData data
                : command.accounts()) {
            if (accountPersistencePort.existsByAccountNumber(data.accountNumber())) {
                throw new IllegalStateException("이미 등록된 기존 은행 계좌입니다.");
            }
            accountPersistencePort.save(Account.importExisting(
                    data.accountNumber(), command.customerId(), data.productId(),
                    AccountType.valueOf(data.accountType()), data.balance(),
                    AccountStatus.valueOf(data.status()), data.passwordHash(),
                    data.openedDate().atStartOfDay(), data.maturityDate()
            ));
        }
    }
}
