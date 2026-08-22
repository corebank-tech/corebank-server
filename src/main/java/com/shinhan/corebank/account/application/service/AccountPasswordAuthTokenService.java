package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerification;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.account.application.port.out.AccountPasswordAuthTokenStorePort;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountPasswordAuthTokenPayload;
import com.shinhan.corebank.account.domain.exception.AccountPasswordErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 고객과 계좌가 일치하고 잠기지 않은 인증 토큰만 원자적으로 소비한다.
@Service
@RequiredArgsConstructor
public class AccountPasswordAuthTokenService
        implements AccountPasswordAuthTokenVerifier {

    private final AccountPasswordAuthTokenStorePort tokenStorePort;
    private final AccountPersistencePort accountPersistencePort;

    @Override
    public void verifyAndConsume(
            AccountPasswordAuthTokenVerification verification
    ) {
        validateVerification(verification);

        Account account = accountPersistencePort
                .findByAccountIdAndCustomerId(
                        verification.accountId(),
                        verification.customerId()
                )
                .orElseThrow(this::invalidToken);

        if (account.isPasswordLocked()) {
            throw new BusinessException(
                    AccountPasswordErrorCode.PASSWORD_LOCKED
            );
        }

        AccountPasswordAuthTokenPayload expectedPayload =
                new AccountPasswordAuthTokenPayload(
                        verification.customerId(),
                        verification.accountId()
                );

        if (!tokenStorePort.consumeIfMatches(
                verification.accountPasswordAuthToken(),
                expectedPayload
        )) {
            throw invalidToken();
        }
    }

    // 토큰 검증에 필요한 모든 입력값이 존재하는지 확인한다.
    private void validateVerification(
            AccountPasswordAuthTokenVerification verification
    ) {
        if (verification == null
                || verification.accountPasswordAuthToken() == null
                || verification.accountPasswordAuthToken().isBlank()
                || verification.customerId() == null
                || verification.accountId() == null) {
            throw invalidToken();
        }
    }

    private BusinessException invalidToken() {
        return new BusinessException(
                AccountPasswordErrorCode.INVALID_AUTH_TOKEN
        );
    }
}
