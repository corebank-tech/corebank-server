package com.shinhan.corebank.account.adapter.out.auth;

import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerification;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerification;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// 출금계좌 등록 인증 Adapter가 각 공개 verifier에 고객·계좌를 정확히 전달하는지 검증한다.
class WithdrawalAccountAuthVerificationAdapterTest {

    private final AccountPasswordAuthTokenVerifier passwordVerifier =
            mock(AccountPasswordAuthTokenVerifier.class);
    private final OtpAuthTokenVerifier otpVerifier =
            mock(OtpAuthTokenVerifier.class);
    private final WithdrawalAccountAuthVerificationAdapter adapter =
            new WithdrawalAccountAuthVerificationAdapter(
                    passwordVerifier,
                    otpVerifier
            );

    @Test
    @DisplayName("계좌비밀번호 인증 토큰을 고객과 대상 계좌에 묶어 검증한다")
    void verifiesAccountPasswordToken() {
        adapter.verifyAccountPasswordToken("password-token", 1L, 101L);

        verify(passwordVerifier).verifyAndConsume(
                new AccountPasswordAuthTokenVerification(
                        "password-token",
                        1L,
                        101L
                )
        );
    }

    @Test
    @DisplayName("OTP 인증 토큰을 출금계좌 등록 유형과 대상 계좌 정보로 검증한다")
    void verifiesOtpToken() {
        adapter.verifyOtpToken("otp-token", 1L, 101L);
        ArgumentCaptor<OtpAuthTokenVerification> captor =
                ArgumentCaptor.forClass(OtpAuthTokenVerification.class);

        verify(otpVerifier).verifyAndConsume(captor.capture());
        OtpAuthTokenVerification verification = captor.getValue();
        assertThat(verification.otpAuthToken()).isEqualTo("otp-token");
        assertThat(verification.customerId()).isEqualTo(1L);
        assertThat(verification.transactionType())
                .isEqualTo(OtpTransactionType.WITHDRAWAL_ACCOUNT_REGISTER);
        assertThat(verification.transactionData())
                .containsEntry("accountId", 101L);
    }
}
