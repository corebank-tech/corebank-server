package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountPasswordAttemptResult;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 비관적 락 안에서 비밀번호 비교와 실패 상태 저장을 완료한다.
@Service
@RequiredArgsConstructor
public class AccountPasswordVerificationProcessor {

    private final AccountPersistencePort accountPersistencePort;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AccountPasswordAttemptResult verify(Long customerId, Long accountId, String accountPassword) {
        Account account = accountPersistencePort
                .findByAccountIdAndCustomerIdForUpdate(accountId, customerId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN));

        if (account.isPasswordLocked()) {
            return account.currentPasswordAttemptResult();
        }

        if (!passwordEncoder.matches(accountPassword, account.getPasswordHash())) {
            AccountPasswordAttemptResult result = account.recordPasswordFailure();
            accountPersistencePort.updatePasswordState(account);
            return result;
        }

        AccountPasswordAttemptResult result = account.recordPasswordSuccess();
        accountPersistencePort.updatePasswordState(account);
        return result;
    }
}
