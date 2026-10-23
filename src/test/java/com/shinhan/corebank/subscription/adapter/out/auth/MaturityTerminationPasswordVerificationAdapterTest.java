package com.shinhan.corebank.subscription.adapter.out.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerification;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.account.domain.exception.AccountPasswordErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("만기해지 계좌비밀번호 인증 어댑터 테스트")
class MaturityTerminationPasswordVerificationAdapterTest {

    private static final String AUTH_TOKEN = "test-account-password-token";
    private static final Long CUSTOMER_ID = 1L;
    private static final Long ACCOUNT_ID = 101L;

    private final AccountPasswordAuthTokenVerifier passwordVerifier = mock(AccountPasswordAuthTokenVerifier.class);

    private final MaturityTerminationPasswordVerificationAdapter adapter =
            new MaturityTerminationPasswordVerificationAdapter(passwordVerifier);

    @Test
    @DisplayName("계좌비밀번호 인증 토큰을 고객과 해지할 상품 계좌에 묶어 검증한다")
    void verifyAndConsume_success() {
        // when
        adapter.verifyAndConsumeAccountPasswordToken(AUTH_TOKEN, CUSTOMER_ID, ACCOUNT_ID);

        // then
        verify(passwordVerifier)
                .verifyAndConsume(new AccountPasswordAuthTokenVerification(AUTH_TOKEN, CUSTOMER_ID, ACCOUNT_ID));
    }

    @Test
    @DisplayName("유효하지 않은 인증 토큰은 APW0102 예외를 그대로 전파한다")
    void verifyAndConsume_invalidToken() {
        // given
        AccountPasswordAuthTokenVerification verification =
                new AccountPasswordAuthTokenVerification(AUTH_TOKEN, CUSTOMER_ID, ACCOUNT_ID);

        doThrow(new BusinessException(AccountPasswordErrorCode.INVALID_AUTH_TOKEN))
                .when(passwordVerifier)
                .verifyAndConsume(verification);

        // when & then
        assertThatThrownBy(() -> adapter.verifyAndConsumeAccountPasswordToken(AUTH_TOKEN, CUSTOMER_ID, ACCOUNT_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(
                                exception.getErrorCode().getCode())
                        .isEqualTo("APW0102"));

        verify(passwordVerifier).verifyAndConsume(verification);
    }
}
