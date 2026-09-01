package com.shinhan.corebank.account.adapter.out.auth;

import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerification;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.account.application.port.out.AccountPasswordChangeAuthVerificationPort;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerification;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 계좌비밀번호 변경의 두 인증 토큰을 각 공개 verifier에 위임한다.
@Component
@RequiredArgsConstructor
public class AccountPasswordChangeAuthVerificationAdapter implements AccountPasswordChangeAuthVerificationPort {

    private final AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;
    private final OtpAuthTokenVerifier otpAuthTokenVerifier;

    @Override
    public void verifyAccountPasswordToken(String token, Long customerId, Long accountId) {
        accountPasswordAuthTokenVerifier.verifyAndConsume(
                new AccountPasswordAuthTokenVerification(token, customerId, accountId));
    }

    @Override
    public void verifyOtpToken(String token, Long customerId, Long accountId) {
        otpAuthTokenVerifier.verifyAndConsume(new OtpAuthTokenVerification(
                token, customerId, OtpTransactionType.ACCOUNT_PASSWORD_CHANGE, Map.of("accountId", accountId)));
    }
}
