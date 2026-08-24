package com.shinhan.corebank.signup.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ApiExceptionHandler;
import com.shinhan.corebank.auth.adapter.in.security.SecurityConfig;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.auth.adapter.in.security.SessionAccessDeniedHandler;
import com.shinhan.corebank.auth.adapter.in.security.SessionAuthenticationEntryPoint;
import com.shinhan.corebank.auth.adapter.in.security.SessionLogoutSuccessHandler;
import com.shinhan.corebank.signup.application.port.in.VerifySignupAccountResult;
import com.shinhan.corebank.signup.application.port.in.VerifySignupAccountUseCase;
import com.shinhan.corebank.signup.domain.exception.AccountVerificationFailedException;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 회원가입 실명·계좌 인증 API의 보안 예외와 응답 계약을 검증한다.
@WebMvcTest(controllers = SignupAccountVerificationController.class)
@TestPropertySource(properties =
        "app.security.cors.allowed-origins=http://localhost:5173")
@Import({
        SecurityConfig.class,
        SessionAuthenticationEntryPoint.class,
        SessionAccessDeniedHandler.class,
        SessionLogoutSuccessHandler.class,
        SignupAccountVerificationExceptionHandler.class,
        ApiExceptionHandler.class
})
class SignupAccountVerificationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean VerifySignupAccountUseCase useCase;

    @Test
    @DisplayName("인증과 CSRF 없이 실명·계좌 인증 토큰을 발급한다")
    void verifiesAccountWithoutAuthenticationOrCsrf() throws Exception {
        given(useCase.verify(any())).willReturn(
                new VerifySignupAccountResult(
                        "ACCOUNT_AUTH_test-token",
                        600L
                )
        );

        mockMvc.perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.accountAuthToken")
                        .value("ACCOUNT_AUTH_test-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(600));
    }

    @Test
    @DisplayName("빈 성명은 400 CMN0001을 반환한다")
    void rejectsBlankUserName() throws Exception {
        assertInvalidRequest("""
                {
                  "userName":"",
                  "birthDate":"900101",
                  "accountNumber":"110123456789",
                  "accountPassword":"1234"
                }
                """);
    }

    @Test
    @DisplayName("생년월일은 YYMMDD 숫자 6자리만 허용한다")
    void rejectsInvalidBirthDate() throws Exception {
        assertInvalidRequest("""
                {
                  "userName":"홍길동",
                  "birthDate":"1990-01-01",
                  "accountNumber":"110123456789",
                  "accountPassword":"1234"
                }
                """);
    }

    @Test
    @DisplayName("계좌번호는 숫자 12자리만 허용한다")
    void rejectsInvalidAccountNumber() throws Exception {
        assertInvalidRequest("""
                {
                  "userName":"홍길동",
                  "birthDate":"900101",
                  "accountNumber":"110-123-456789",
                  "accountPassword":"1234"
                }
                """);
    }

    @Test
    @DisplayName("계좌비밀번호는 숫자 4자리만 허용한다")
    void rejectsInvalidAccountPassword() throws Exception {
        assertInvalidRequest("""
                {
                  "userName":"홍길동",
                  "birthDate":"900101",
                  "accountNumber":"110123456789",
                  "accountPassword":"12345"
                }
                """);
    }

    @Test
    @DisplayName("이미 가입된 원장 고객은 409 ATH0303을 반환한다")
    void alreadyRegisteredExistingBankCustomerReturnsConflict()
            throws Exception {
        given(useCase.verify(any())).willThrow(new BusinessException(
                SignupErrorCode.DUPLICATE_EXISTING_BANK_CUSTOMER
        ));

        mockMvc.perform(validRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATH0303"));
    }

    @Test
    @DisplayName("고객 또는 계좌 정보 불일치는 ATH0009와 data null이다")
    void informationMismatchReturnsNoAttempts() throws Exception {
        given(useCase.verify(any())).willThrow(
                AccountVerificationFailedException.informationMismatch()
        );

        mockMvc.perform(validRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ATH0009"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("비밀번호 불일치는 ATH0009와 실패 횟수를 반환한다")
    void passwordMismatchReturnsAttempts() throws Exception {
        given(useCase.verify(any())).willThrow(
                AccountVerificationFailedException.passwordMismatch(3, 2)
        );

        mockMvc.perform(validRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ATH0009"))
                .andExpect(jsonPath("$.data.errorCount").value(3))
                .andExpect(jsonPath("$.data.remainingAttempts").value(2));
    }

    @Test
    @DisplayName("거래정지 계좌는 ATH0102와 5회 실패 결과를 반환한다")
    void lockedAccountReturnsAttempts() throws Exception {
        given(useCase.verify(any())).willThrow(
                AccountVerificationFailedException.locked(5, 0)
        );

        mockMvc.perform(validRequest())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ATH0102"))
                .andExpect(jsonPath("$.data.errorCount").value(5))
                .andExpect(jsonPath("$.data.remainingAttempts").value(0));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    validRequest() {
        return post("/api/v1/auth/verify-account")
                .contextPath("/api/v1")
                .contentType(APPLICATION_JSON)
                .content("""
                        {
                          "userName":"홍길동",
                          "birthDate":"900101",
                          "accountNumber":"110123456789",
                          "accountPassword":"1234"
                        }
                        """);
    }

    private void assertInvalidRequest(String content) throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-account")
                        .contextPath("/api/v1")
                .contentType(APPLICATION_JSON)
                .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"))
                .andExpect(jsonPath("$.message")
                        .value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(useCase, never()).verify(any());
    }
}
