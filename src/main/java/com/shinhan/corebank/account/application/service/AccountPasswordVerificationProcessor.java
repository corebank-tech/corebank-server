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

    // 공개 인증 API에서 계좌 존재·소유 여부에 따른 BCrypt 처리 시간 차이를 줄이기 위한 고정 해시다.
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$TKFDPDX3R71KXVXhzHGIfuD5TTj2R8Z0uSnVgFKgO5B5p3vpqI1CG";

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

    // 계좌정보 불일치 경로에서도 실제 검증과 같은 BCrypt 비용을 지불하되 계좌 상태는 변경하지 않는다.
    public void performDummyVerification(String accountPassword) {
        passwordEncoder.matches(accountPassword, DUMMY_PASSWORD_HASH);
    }
}
