package com.shinhan.corebank.account.adapter.out.auth;

import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerification;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.account.application.port.out.WithdrawalAccountAuthVerificationPort;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerification;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

// 출금계좌 등록의 계좌비밀번호·OTP 토큰을 실제 공개 verifier로 검증한다.
@Component
@RequiredArgsConstructor
public class WithdrawalAccountAuthVerificationAdapter
        implements WithdrawalAccountAuthVerificationPort {

    private final AccountPasswordAuthTokenVerifier
            accountPasswordAuthTokenVerifier;
    private final OtpAuthTokenVerifier otpAuthTokenVerifier;

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

    @Override
    public void verifyOtpToken(
            String token,
            Long customerId,
            Long accountId
    ) {
        otpAuthTokenVerifier.verifyAndConsume(
                new OtpAuthTokenVerification(
                        token,
                        customerId,
                        OtpTransactionType
                                .WITHDRAWAL_ACCOUNT_REGISTER,
                        Map.of("accountId", accountId)
                )
        );
    }
}
