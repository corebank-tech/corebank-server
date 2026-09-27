package com.shinhan.corebank.subscription.adapter.out.auth;

import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerification;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.subscription.application.port.out.ProductSubscriptionPasswordVerificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 상품가입 실행의 계좌비밀번호 토큰을 출금계좌에 묶어 계좌 공개 verifier로 검증·소비한다.
@Component
@RequiredArgsConstructor
public class ProductSubscriptionPasswordVerificationAdapter implements ProductSubscriptionPasswordVerificationPort {

    private final AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    @Override
    public void verifyAndConsumeAccountPasswordToken(String token, Long customerId, Long accountId) {
        accountPasswordAuthTokenVerifier.verifyAndConsume(
                new AccountPasswordAuthTokenVerification(token, customerId, accountId));
    }
}
