package com.shinhan.corebank.account.adapter.out.auth;

import com.shinhan.corebank.account.application.port.out.WithdrawalAccountAuthVerificationPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 계좌 비밀번호·OTP 인증 기능 연동 전까지 사용하는 임시 Adapter.
 * P6 구현 완료 후 실제 인증 Adapter로 교체한다.
 */
@Component
@Profile({"local", "test", "scratch"})
public class WithdrawalAccountAuthTokenMockAdapter
        implements WithdrawalAccountAuthVerificationPort {

    @Override
    public void verifyAccountPasswordToken(
            String token,
            Long customerId,
            Long accountId
    ) {
        // TODO P6 AccountPasswordAuthTokenVerifier 연동
    }

    @Override
    public void verifyOtpToken(
            String token,
            Long customerId,
            Long accountId
    ) {
        // TODO P6 OtpAuthTokenVerifier 연동
    }
}