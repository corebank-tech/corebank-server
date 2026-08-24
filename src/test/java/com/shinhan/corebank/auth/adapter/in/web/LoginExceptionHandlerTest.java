package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.domain.exception.LoginFailedException;
import com.shinhan.corebank.auth.domain.model.LoginAttemptResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class LoginExceptionHandlerTest {

    private final LoginExceptionHandler handler =
            new LoginExceptionHandler();

    @Test
    @DisplayName("존재하지 않는 아이디도 비밀번호 불일치와 동일한 형태의 데이터를 반환한다 (REQ-AUTH-023)")
    void handlesCustomerNotFoundWithDecoyAttemptData() {
        ResponseEntity<ErrorResponse> response =
                handler.handleLoginFailure(
                        LoginFailedException.invalidCredentials(
                                new LoginAttemptResult(1, 4)
                        )
                );

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ATH0101");
        assertThat(response.getBody().data()).isEqualTo(
                new LoginFailureData(1, 4)
        );
    }

    @Test
    @DisplayName("비밀번호 불일치는 ATH0101과 최신 실패 횟수를 반환한다")
    void handlesInvalidCredentials() {
        ResponseEntity<ErrorResponse> response =
                handler.handleLoginFailure(
                        LoginFailedException.invalidCredentials(
                                new LoginAttemptResult(2, 3)
                        )
                );

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ATH0101");
        assertThat(response.getBody().data()).isEqualTo(
                new LoginFailureData(2, 3)
        );
    }

    @Test
    @DisplayName("잠긴 계정은 ATH0102와 빈 데이터를 반환한다")
    void handlesAccountLocked() {
        ResponseEntity<ErrorResponse> response =
                handler.handleLoginFailure(
                        LoginFailedException.accountLocked()
                );

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ATH0102");
        assertThat(response.getBody().data()).isNull();
    }
}
