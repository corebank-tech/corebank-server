package com.shinhan.corebank.limit.adapter.out.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("이체한도 인증 토큰 어댑터 단위 테스트")
class TransferLimitAuthTokenVerificationAdapterTest {

    private final AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier =
            mock(AccountPasswordAuthTokenVerifier.class);
    private final OtpAuthTokenVerifier otpAuthTokenVerifier = mock(OtpAuthTokenVerifier.class);
    private final TransferLimitAuthTokenVerificationAdapter adapter =
            new TransferLimitAuthTokenVerificationAdapter(accountPasswordAuthTokenVerifier, otpAuthTokenVerifier);

    @Test
    @DisplayName("계좌비밀번호 토큰을 현재 고객 기준으로 검증하고 소비한다")
    void verifiesAccountPasswordTokenByCustomer() {
        adapter.verifyAccountPassword("account-password-token", 1L);

        verify(accountPasswordAuthTokenVerifier).verifyAndConsume("account-password-token", 1L);
    }
}
