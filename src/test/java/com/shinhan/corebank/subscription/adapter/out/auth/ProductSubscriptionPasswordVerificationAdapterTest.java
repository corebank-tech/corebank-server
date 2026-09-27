package com.shinhan.corebank.subscription.adapter.out.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerification;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 상품가입 인증 Adapter가 계좌비밀번호 공개 verifier에 고객·출금계좌를 정확히 전달하는지 검증한다.
class ProductSubscriptionPasswordVerificationAdapterTest {

    private final AccountPasswordAuthTokenVerifier passwordVerifier = mock(AccountPasswordAuthTokenVerifier.class);
    private final ProductSubscriptionPasswordVerificationAdapter adapter =
            new ProductSubscriptionPasswordVerificationAdapter(passwordVerifier);

    @Test
    @DisplayName("계좌비밀번호 인증 토큰을 고객과 출금계좌에 묶어 검증한다")
    void verifiesAccountPasswordToken() {
        adapter.verifyAccountPasswordToken("password-token", 1L, 101L);

        verify(passwordVerifier).verifyAndConsume(new AccountPasswordAuthTokenVerification("password-token", 1L, 101L));
    }
}
