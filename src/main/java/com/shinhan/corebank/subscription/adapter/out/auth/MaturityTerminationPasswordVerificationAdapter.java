package com.shinhan.corebank.subscription.adapter.out.auth;

import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerification;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.subscription.application.port.out.MaturityTerminationPasswordVerificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MaturityTerminationPasswordVerificationAdapter implements MaturityTerminationPasswordVerificationPort {

    private final AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    @Override
    public void verifyAndConsumeAccountPasswordToken(String token, Long customerId, Long accountId) {

        accountPasswordAuthTokenVerifier.verifyAndConsume(
                new AccountPasswordAuthTokenVerification(token, customerId, accountId));
    }
}
