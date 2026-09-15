package com.shinhan.corebank.scheduledtransfer.adapter.out.auth;

import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerification;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.scheduledtransfer.application.port.out.AuthTokenVerificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 빈 이름 명시: autotransfer.adapter.out.auth.AuthTokenVerificationAdapter와 클래스 단순이름이 같아
// 기본 빈 이름(authTokenVerificationAdapter)이 충돌한다.
@Component("scheduledTransferAuthTokenVerificationAdapter")
@RequiredArgsConstructor
public class AuthTokenVerificationAdapter implements AuthTokenVerificationPort {

    private final AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    @Override
    public void verifyAndConsume(String authToken, Long customerId, Long accountId) {
        accountPasswordAuthTokenVerifier.verifyAndConsume(
                new AccountPasswordAuthTokenVerification(authToken, customerId, accountId));
    }
}
