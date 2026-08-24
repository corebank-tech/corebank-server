package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.account.api.ExistingAccountRegistration;
import com.shinhan.corebank.account.api.RegisterExistingAccountsCommand;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.customer.api.CustomerRegistration;
import com.shinhan.corebank.customer.api.RegisterCustomerCommand;
import com.shinhan.corebank.customer.api.RegisteredCustomer;
import com.shinhan.corebank.limit.api.TransferLimitRegistration;
import com.shinhan.corebank.signup.application.port.out.SignupTermsAgreementPort;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import com.shinhan.corebank.signup.domain.model.ExistingBankAccountSnapshot;
import com.shinhan.corebank.signup.domain.model.SignupCompletionSnapshot;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 고객·이체한도·약관 동의·전체 계좌를 하나의 MySQL 트랜잭션으로 저장한다.
@Service
public class SignupCompletionTransactionService {

    private static final String EXISTING_BANK_CUSTOMER_UNIQUE_KEY =
            "uk_customer_existing_bank_customer_id";

    private final CustomerRegistration customerRegistration;
    private final TransferLimitRegistration transferLimitRegistration;
    private final SignupTermsAgreementPort termsAgreementPort;
    private final ExistingAccountRegistration accountRegistration;

    public SignupCompletionTransactionService(
            CustomerRegistration customerRegistration,
            TransferLimitRegistration transferLimitRegistration,
            SignupTermsAgreementPort termsAgreementPort,
            ExistingAccountRegistration accountRegistration
    ) {
        this.customerRegistration = customerRegistration;
        this.transferLimitRegistration = transferLimitRegistration;
        this.termsAgreementPort = termsAgreementPort;
        this.accountRegistration = accountRegistration;
    }

    @Transactional
    public RegisteredCustomer register(
            SignupCompletionSnapshot snapshot,
            LocalDateTime joinedAt
    ) {
        RegisteredCustomer customer = registerCustomer(
                new RegisterCustomerCommand(
                        snapshot.signup().userId(),
                        snapshot.signup().existingBankCustomerId(),
                        snapshot.signup().passwordHash(),
                        snapshot.customerProfile().userName(),
                        snapshot.customerProfile().birthDate(),
                        snapshot.signup().email(),
                        snapshot.signup().phoneNumber(),
                        joinedAt
                )
        );

        transferLimitRegistration.registerDefault(customer.customerId());

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

    // 원장 고객 선검증과 INSERT 사이의 시간차 경합(TOCTOU)은 UK 제약만 막는다.
    // 그 위반을 선검증과 같은 ATH0303 으로 변환해 500 으로 새어나가지 않게 한다.
    private RegisteredCustomer registerCustomer(
            RegisterCustomerCommand command
    ) {
        try {
            return customerRegistration.register(command);
        } catch (DataIntegrityViolationException exception) {
            if (violatesExistingBankCustomerKey(exception)) {
                throw new BusinessException(
                        SignupErrorCode.DUPLICATE_EXISTING_BANK_CUSTOMER,
                        exception
                );
            }
            // 다른 제약 위반까지 ATH0303 으로 뭉뚱그리면 틀린 안내가 되므로
            // 원래 예외를 그대로 던져 종전 동작을 유지한다.
            throw exception;
        }
    }

    // MySQL 은 위반한 키를 'customer.uk_...' 처럼 테이블명과 함께 알려주므로
    // 이름 일치가 아니라 포함 여부로 판정한다.
    private boolean violatesExistingBankCustomerKey(
            DataIntegrityViolationException exception
    ) {
        if (!(exception.getCause()
                instanceof ConstraintViolationException violation)) {
            return false;
        }

        String constraintName = violation.getConstraintName();
        return constraintName != null
                && constraintName.contains(EXISTING_BANK_CUSTOMER_UNIQUE_KEY);
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
