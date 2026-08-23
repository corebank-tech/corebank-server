package com.shinhan.corebank.account.adapter.out.auth;

import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerification;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.account.application.port.out.WithdrawalAccountPasswordVerificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 출금계좌 등록의 계좌비밀번호 토큰을 실제 공개 verifier(P6)로 검증한다.
@Component
@RequiredArgsConstructor
public class WithdrawalAccountPasswordVerificationAdapter
        implements WithdrawalAccountPasswordVerificationPort {

    private final AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    @Override
    public void verifyAccountPasswordToken(
            String token,
            Long customerId,
            Long accountId
    ) {
        accountPasswordAuthTokenVerifier.verifyAndConsume(
                new AccountPasswordAuthTokenVerification(
                        token,
                        customerId,
                        accountId
                )
        );
    }
}
