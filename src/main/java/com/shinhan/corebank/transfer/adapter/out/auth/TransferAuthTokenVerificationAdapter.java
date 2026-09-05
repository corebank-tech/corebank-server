package com.shinhan.corebank.transfer.adapter.out.auth;

import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerification;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.transfer.application.port.out.TransferAuthTokenVerificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferAuthTokenVerificationAdapter implements TransferAuthTokenVerificationPort {

    private final AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    @Override
    public void verify(String authToken, Long customerId, Long accountId) {
        accountPasswordAuthTokenVerifier.verifyAndConsume(
                new AccountPasswordAuthTokenVerification(authToken, customerId, accountId));
    }
}
