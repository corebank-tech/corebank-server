package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.api.AccountIdentityVerification;
import com.shinhan.corebank.account.api.AccountIdentityVerificationResult;
import com.shinhan.corebank.account.api.AccountIdentityVerificationStatus;
import com.shinhan.corebank.account.api.AccountIdentityVerifier;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountPasswordAttemptResult;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 후보 고객의 계좌 소유권을 확인한 뒤 기존 잠금 정책으로 계좌비밀번호를 검증한다.
@Service
@RequiredArgsConstructor
public class AccountIdentityVerificationService implements AccountIdentityVerifier {

    private final AccountPersistencePort accountPersistencePort;
    private final AccountPasswordVerificationProcessor passwordProcessor;

    @Override
    public AccountIdentityVerificationResult verify(AccountIdentityVerification verification) {
        Objects.requireNonNull(verification, "verification must not be null");
        Objects.requireNonNull(verification.candidateCustomerIds(), "candidateCustomerIds must not be null");

        Account account = accountPersistencePort
                .findByAccountNumber(verification.accountNumber())
                .orElse(null);

        if (account == null || !verification.candidateCustomerIds().contains(account.getCustomerId())) {
            return result(AccountIdentityVerificationStatus.INFORMATION_MISMATCH, null);
        }

        AccountPasswordAttemptResult attempt = passwordProcessor.verify(
                account.getCustomerId(), account.getAccountId(), verification.accountPassword());

        if (attempt.locked()) {
            return result(AccountIdentityVerificationStatus.LOCKED, account.getCustomerId());
        }
        if (!attempt.matched()) {
            return result(AccountIdentityVerificationStatus.PASSWORD_MISMATCH, account.getCustomerId());
        }
        return result(AccountIdentityVerificationStatus.VERIFIED, account.getCustomerId());
    }

    private AccountIdentityVerificationResult result(AccountIdentityVerificationStatus status, Long customerId) {
        return new AccountIdentityVerificationResult(status, customerId);
    }
}
