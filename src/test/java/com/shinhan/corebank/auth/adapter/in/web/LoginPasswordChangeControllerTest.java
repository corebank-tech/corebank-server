package com.shinhan.corebank.auth.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.auth.adapter.in.security.SecurityConfig;
import com.shinhan.corebank.auth.adapter.in.security.SessionAccessDeniedHandler;
import com.shinhan.corebank.auth.adapter.in.security.SessionAuthenticationEntryPoint;
import com.shinhan.corebank.auth.adapter.in.security.SessionLogoutSuccessHandler;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.auth.application.port.in.ChangeLoginPasswordResult;
import com.shinhan.corebank.auth.application.port.in.ChangeLoginPasswordUseCase;
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

// 로그인·CSRF·멱등키와 비밀번호 변경 응답 계약을 검증한다.
@WebMvcTest(controllers = LoginPasswordChangeController.class)
@Import({
    SecurityConfig.class,
    SessionAuthenticationEntryPoint.class,
    SessionAccessDeniedHandler.class,
    SessionLogoutSuccessHandler.class,
    IdempotentRequestExecutor.class,
    ClientIpResolver.class
})
class LoginPasswordChangeControllerTest {
    private static final String KEY = "550e8400-e29b-41d4-a716-446655440000";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChangeLoginPasswordUseCase useCase;

    @MockitoBean
    CurrentCustomerProvider currentCustomerProvider;

    @MockitoBean
    IdempotencyService idempotencyService;

    @Test
    @DisplayName("로그인 고객은 현재 비밀번호 확인 후 비밀번호를 변경한다")
    void changesPassword() throws Exception {
        given(currentCustomerProvider.getCurrentCustomer()).willReturn(new AuthenticatedCustomer(1L, "user01", "홍길동"));
        given(idempotencyService.begin(any(), any(), any(), any())).willReturn(IdempotencyResult.proceed());
        given(useCase.change(any()))
                .willReturn(new ChangeLoginPasswordResult(1L, OffsetDateTime.parse("2026-09-20T18:30:00+09:00")));

        mockMvc.perform(put("/api/v1/customers/me/password")
                        .contextPath("/api/v1")
                        .with(user("user01"))
                        .with(csrf())
                        .header("Idempotency-Key", KEY)
                        .contentType(APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.customerId").value(1))
                .andExpect(jsonPath("$.data.passwordChangedAt").value("2026-09-20T18:30:00+09:00"));

        verify(idempotencyService).complete(any(), anyShort(), any());
    }

    @Test
    @DisplayName("비로그인 요청은 CMN0101을 반환한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(put("/api/v1/customers/me/password")
                        .contextPath("/api/v1")
                        .with(csrf())
                        .header("Idempotency-Key", KEY)
                        .contentType(APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMN0101"));
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 비밀번호 변경을 차단한다")
    void rejectsMissingCsrf() throws Exception {
        mockMvc.perform(put("/api/v1/customers/me/password")
                        .contextPath("/api/v1")
                        .with(user("user01"))
                        .header("Idempotency-Key", KEY)
                        .contentType(APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden());
        verify(useCase, never()).change(any());
    }

    @Test
    @DisplayName("멱등키가 없으면 CMN0002를 반환한다")
    void rejectsMissingIdempotencyKey() throws Exception {
        mockMvc.perform(put("/api/v1/customers/me/password")
                        .contextPath("/api/v1")
                        .with(user("user01"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
        verify(useCase, never()).change(any());
    }

    private String body() {
        return """
                {"currentPassword":"Current1!","newPassword":"NewPass8!x","newPasswordConfirm":"NewPass8!x"}
                """;
    }
}
