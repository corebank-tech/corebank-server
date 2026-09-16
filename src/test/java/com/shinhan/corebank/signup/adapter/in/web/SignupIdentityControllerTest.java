package com.shinhan.corebank.signup.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.auth.adapter.in.security.SecurityConfig;
import com.shinhan.corebank.auth.adapter.in.security.SessionAccessDeniedHandler;
import com.shinhan.corebank.auth.adapter.in.security.SessionAuthenticationEntryPoint;
import com.shinhan.corebank.auth.adapter.in.security.SessionLogoutSuccessHandler;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.signup.application.port.in.CheckUserIdResult;
import com.shinhan.corebank.signup.application.port.in.CheckUserIdUseCase;
import com.shinhan.corebank.signup.application.port.in.IssueEmailVerificationResult;
import com.shinhan.corebank.signup.application.port.in.IssueEmailVerificationUseCase;
import com.shinhan.corebank.signup.application.port.in.VerifyEmailResult;
import com.shinhan.corebank.signup.application.port.in.VerifyEmailUseCase;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SignupIdentityController.class)
@TestPropertySource(properties = "app.security.cors.allowed-origins=http://localhost:5173")
@Import({
    SecurityConfig.class,
    SessionAuthenticationEntryPoint.class,
    SessionAccessDeniedHandler.class,
    SessionLogoutSuccessHandler.class
})
class SignupIdentityControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CheckUserIdUseCase checkUserIdUseCase;

    @MockitoBean
    IssueEmailVerificationUseCase issueUseCase;

    @MockitoBean
    VerifyEmailUseCase verifyUseCase;

    @Test
    @DisplayName("인증과 CSRF 없이 아이디 중복확인 토큰을 발급한다")
    void checksUserIdWithoutAuthenticationOrCsrf() throws Exception {
        given(checkUserIdUseCase.check(any())).willReturn(new CheckUserIdResult(true, "USER_ID_CHECK_token", 180));

        mockMvc.perform(post("/api/v1/auth/check-id")
                        .contextPath("/api/v1")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"user1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.isAvailable").value(true))
                .andExpect(jsonPath("$.data.userIdCheckToken").value("USER_ID_CHECK_token"))
                .andExpect(jsonPath("$.data.expiresIn").value(180));
    }

    @Test
    @DisplayName("중복 아이디는 409 ATH0301을 반환한다")
    void duplicateUserIdReturnsContractError() throws Exception {
        given(checkUserIdUseCase.check(any())).willThrow(new BusinessException(SignupErrorCode.DUPLICATE_USER_ID));

        mockMvc.perform(post("/api/v1/auth/check-id")
                        .contextPath("/api/v1")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"user1234\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATH0301"));
    }

    @Test
    @DisplayName("인증과 CSRF 없이 이메일 인증번호를 발급한다")
    void issuesEmailVerificationWithoutAuthenticationOrCsrf() throws Exception {
        given(issueUseCase.issue(any())).willReturn(new IssueEmailVerificationResult("EVF_request", "012345", 180));

        mockMvc.perform(
                        post("/api/v1/auth/email-verifications")
                                .contextPath("/api/v1")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "email":"user@example.com",
                                  "purpose":"SIGN_UP"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailVerificationId").value("EVF_request"))
                .andExpect(jsonPath("$.data.verificationCode").value("012345"))
                .andExpect(jsonPath("$.data.expiresIn").value(180));
    }

    @Test
    @DisplayName("잘못된 이메일 형식은 400 CMN0001을 반환한다")
    void invalidEmailReturnsInvalidInput() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/email-verifications")
                                .contextPath("/api/v1")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "email":"not-email",
                                  "purpose":"SIGN_UP"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("인증과 CSRF 없이 인증번호를 검증하고 토큰을 발급한다")
    void verifiesEmailWithoutAuthenticationOrCsrf() throws Exception {
        given(verifyUseCase.verify(any()))
                .willReturn(new VerifyEmailResult(
                        "EMAIL_VERIFICATION_token", OffsetDateTime.parse("2026-08-19T10:00:00+09:00")));

        mockMvc.perform(post("/api/v1/auth/email-verifications/EVF_request/verify")
                        .contextPath("/api/v1")
                        .contentType(APPLICATION_JSON)
                        .content("{\"verificationCode\":\"012345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailVerificationToken").value("EMAIL_VERIFICATION_token"))
                .andExpect(jsonPath("$.data.verifiedAt").value("2026-08-19T10:00:00+09:00"));
    }
}
