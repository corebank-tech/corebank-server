package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.account.api.ExistingAccountRegistration;
import com.shinhan.corebank.account.api.RegisterExistingAccountsCommand;
import com.shinhan.corebank.customer.api.CustomerRegistration;
import com.shinhan.corebank.customer.api.RegisterCustomerCommand;
import com.shinhan.corebank.customer.api.RegisteredCustomer;
import com.shinhan.corebank.signup.application.port.out.SignupTermsAgreementPort;
import com.shinhan.corebank.signup.domain.model.ExistingBankAccountSnapshot;
import com.shinhan.corebank.signup.domain.model.SignupCompletionSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 고객·약관 동의·전체 계좌를 하나의 MySQL 트랜잭션으로 저장한다.
@Service
public class SignupCompletionTransactionService {

    private final CustomerRegistration customerRegistration;
    private final SignupTermsAgreementPort termsAgreementPort;
    private final ExistingAccountRegistration accountRegistration;

    public SignupCompletionTransactionService(
            CustomerRegistration customerRegistration,
            SignupTermsAgreementPort termsAgreementPort,
            ExistingAccountRegistration accountRegistration
    ) {
        this.customerRegistration = customerRegistration;
        this.termsAgreementPort = termsAgreementPort;
        this.accountRegistration = accountRegistration;
    }

    @Transactional
    public RegisteredCustomer register(
            SignupCompletionSnapshot snapshot,
            LocalDateTime joinedAt
    ) {
        RegisteredCustomer customer = customerRegistration.register(
                new RegisterCustomerCommand(
                        snapshot.signup().userId(),
                        snapshot.signup().passwordHash(),
                        snapshot.customerProfile().userName(),
                        snapshot.customerProfile().birthDate(),
                        snapshot.signup().email(),
                        snapshot.signup().phoneNumber(),
                        joinedAt
                )
        );

        termsAgreementPort.saveAll(
                customer.customerId(),
                snapshot.signup().agreedTerms(),
                joinedAt
        );
        accountRegistration.registerAll(new RegisterExistingAccountsCommand(
                customer.customerId(),
                snapshot.accounts().stream()
                        .map(this::toAccountData)
                        .toList()
        ));
        return customer;
    }

    private RegisterExistingAccountsCommand.AccountData toAccountData(
            ExistingBankAccountSnapshot account
    ) {
        return new RegisterExistingAccountsCommand.AccountData(
                account.accountNumber(),
                account.accountType(),
                account.productId(),
                account.balance(),
                account.status(),
                account.passwordHash(),
                account.openedDate(),
                account.maturityDate()
        );
    }
}
