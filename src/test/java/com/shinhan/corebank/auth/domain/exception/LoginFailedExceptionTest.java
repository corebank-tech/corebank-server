package com.shinhan.corebank.auth.domain.exception;

import com.shinhan.corebank.auth.domain.model.LoginAttemptResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("로그인 실패 예외 단위 테스트")
class LoginFailedExceptionTest {

    // 잠금 전 비밀번호 불일치는 횟수 데이터를 포함
    @Test
    @DisplayName("비밀번호 불일치는 ATH0101과 시도 결과를 가진다")
    void createsInvalidCredentialsFailure() {
        LoginAttemptResult attemptResult =
                new LoginAttemptResult(4, 1);

        LoginFailedException exception =
                LoginFailedException.invalidCredentials(attemptResult);

        assertThat(exception.getErrorCode())
                .isEqualTo(AuthErrorCode.LOGIN_FAILED);
        assertThat(exception.getAttemptResult())
                .contains(attemptResult);
    }

    // 잠긴 계정은 실패 횟수 데이터를 노출하지 않음
    @Test
    @DisplayName("계정 잠금은 ATH0102와 빈 시도 결과를 가진다")
    void createsAccountLockedFailure() {
        LoginFailedException exception =
                LoginFailedException.accountLocked();

        assertThat(exception.getErrorCode())
                .isEqualTo(AuthErrorCode.ACCOUNT_LOCKED);
        assertThat(exception.getAttemptResult()).isEmpty();
    }
}
