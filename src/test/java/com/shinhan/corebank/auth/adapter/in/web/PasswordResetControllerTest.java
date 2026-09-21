package com.shinhan.corebank.auth.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.auth.adapter.in.security.SecurityConfig;
import com.shinhan.corebank.auth.adapter.in.security.SessionAccessDeniedHandler;
import com.shinhan.corebank.auth.adapter.in.security.SessionAuthenticationEntryPoint;
import com.shinhan.corebank.auth.adapter.in.security.SessionLogoutSuccessHandler;
import com.shinhan.corebank.auth.application.port.in.IssuePasswordResetResult;
import com.shinhan.corebank.auth.application.port.in.IssuePasswordResetUseCase;
import com.shinhan.corebank.auth.application.port.in.ResetPasswordResult;
import com.shinhan.corebank.auth.application.port.in.ResetPasswordUseCase;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.idempotency.IdempotencyResult;
import com.shinhan.corebank.common.idempotency.IdempotencyService;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PasswordResetController.class)
@Import({
    SecurityConfig.class,
    SessionAuthenticationEntryPoint.class,
    SessionAccessDeniedHandler.class,
    SessionLogoutSuccessHandler.class,
    IdempotentRequestExecutor.class
})
@DisplayName("비밀번호 재설정 Controller 테스트")
class PasswordResetControllerTest {

    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    IssuePasswordResetUseCase issueUseCase;

    @MockitoBean
    ResetPasswordUseCase resetUseCase;

    @MockitoBean
    IdempotencyService idempotencyService;

    @Test
    @DisplayName("인증 없이 CSRF 토큰 없이 인증번호를 발급한다")
    void issuesCodeWithoutAuthenticationOrCsrf() throws Exception {
        given(issueUseCase.issue(any())).willReturn(new IssuePasswordResetResult("PRR_test", "498210", 180));

        mockMvc.perform(
                        post("/api/v1/auth/password-reset-requests")
                                .contextPath("/api/v1")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "userId": "user01",
                                  "customerName": "홍길동",
                                  "email": "user@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.passwordResetRequestId").value("PRR_test"))
                .andExpect(jsonPath("$.data.verificationCode").value("498210"))
                .andExpect(jsonPath("$.data.expiresIn").value(180));
    }

    @Test
    @DisplayName("인증 없이 CSRF 토큰 없이 비밀번호를 재설정한다")
    void resetsPasswordWithoutAuthenticationOrCsrf() throws Exception {
        OffsetDateTime changedAt = OffsetDateTime.parse("2026-09-17T10:00:00+09:00");
        given(resetUseCase.resolveCustomerId("PRR_test")).willReturn(1L);
        given(idempotencyService.begin(any(), any(), any(), any())).willReturn(IdempotencyResult.proceed());
        given(resetUseCase.reset(any())).willReturn(new ResetPasswordResult(1L, changedAt));

        mockMvc.perform(
                        put("/api/v1/auth/password-reset-requests/PRR_test")
                                .contextPath("/api/v1")
                                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "verificationCode": "498210",
                                  "newPassword": "NewPassword1!",
                                  "newPasswordConfirm": "NewPassword1!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.changedAt").value("2026-09-17T10:00:00+09:00"));

        verify(idempotencyService).complete(any(), anyShort(), any());
        verify(idempotencyService)
                .begin(
                        eq(IDEMPOTENCY_KEY),
                        eq(1L),
                        eq("PUT /auth/password-reset-requests/PRR_test"),
                        eq("{\"customerId\":1,\"newPassword\":\"NewPassword1!\","
                                + "\"newPasswordConfirm\":\"NewPassword1!\","
                                + "\"passwordResetRequestId\":\"PRR_test\",\"verificationCode\":\"498210\"}"));
    }

    @Test
    @DisplayName("비밀번호 재설정 멱등키가 누락되면 CMN0002를 반환한다")
    void rejectsMissingIdempotencyKey() throws Exception {
        mockMvc.perform(
                        put("/api/v1/auth/password-reset-requests/PRR_test")
                                .contextPath("/api/v1")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "verificationCode": "498210",
                                  "newPassword": "NewPassword1!",
                                  "newPasswordConfirm": "NewPassword1!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));

        verify(resetUseCase, never()).resolveCustomerId(any());
    }

    @Test
    @DisplayName("인증번호 발급 필수값이 누락되면 CMN0002를 반환한다")
    void rejectsMissingIssueFields() throws Exception {
        given(issueUseCase.issue(any())).willThrow(new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING));

        mockMvc.perform(post("/api/v1/auth/password-reset-requests")
                        .contextPath("/api/v1")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
    }
}
