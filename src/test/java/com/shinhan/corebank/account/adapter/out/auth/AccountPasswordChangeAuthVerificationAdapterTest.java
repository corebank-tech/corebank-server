package com.shinhan.corebank.account.adapter.out.auth;

import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerification;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerification;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// 비밀번호 변경 Adapter가 두 공개 인증 계약에 정확한 거래 정보를 전달하는지 검증한다.
class AccountPasswordChangeAuthVerificationAdapterTest {

    private final AccountPasswordAuthTokenVerifier passwordVerifier =
            mock(AccountPasswordAuthTokenVerifier.class);
    private final OtpAuthTokenVerifier otpVerifier =
            mock(OtpAuthTokenVerifier.class);
    private final AccountPasswordChangeAuthVerificationAdapter adapter =
            new AccountPasswordChangeAuthVerificationAdapter(
                    passwordVerifier,
                    otpVerifier
            );

    @Test
    @DisplayName("계좌비밀번호 인증 토큰을 고객과 대상 계좌에 묶어 소비한다")
    void verifiesAccountPasswordToken() {
        adapter.verifyAccountPasswordToken(
                "password-token",
                1L,
                101L
        );

        verify(passwordVerifier).verifyAndConsume(
                new AccountPasswordAuthTokenVerification(
                        "password-token",
                        1L,
                        101L
                )
        );
    }

    @Test
    @DisplayName("OTP를 계좌비밀번호 변경 유형과 accountId 거래정보로 소비한다")
    void verifiesOtpToken() {
        adapter.verifyOtpToken("otp-token", 1L, 101L);

        verify(otpVerifier).verifyAndConsume(
                new OtpAuthTokenVerification(
                        "otp-token",
                        1L,
                        OtpTransactionType.ACCOUNT_PASSWORD_CHANGE,
                        Map.of("accountId", 101L)
                )
        );
    }
}
