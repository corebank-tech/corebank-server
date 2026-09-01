package com.shinhan.corebank.signup.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.auth.adapter.in.security.SecurityConfig;
import com.shinhan.corebank.auth.adapter.in.security.SessionAccessDeniedHandler;
import com.shinhan.corebank.auth.adapter.in.security.SessionAuthenticationEntryPoint;
import com.shinhan.corebank.auth.adapter.in.security.SessionLogoutSuccessHandler;
import com.shinhan.corebank.common.idempotency.IdempotencyResult;
import com.shinhan.corebank.common.idempotency.IdempotencyService;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.signup.application.port.in.CompleteSignupResult;
import com.shinhan.corebank.signup.application.port.in.CompleteSignupUseCase;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 회원가입 완료 API의 공개 접근·멱등키·응답 계약을 검증한다.
@WebMvcTest(controllers = SignupCompletionController.class)
@TestPropertySource(properties = "app.security.cors.allowed-origins=http://localhost:5173")
@Import({
    SecurityConfig.class,
    SessionAuthenticationEntryPoint.class,
    SessionAccessDeniedHandler.class,
    SessionLogoutSuccessHandler.class,
    IdempotentRequestExecutor.class
})
class SignupCompletionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CompleteSignupUseCase completeSignupUseCase;

    @MockitoBean
    IdempotencyService idempotencyService;

    @Test
    void completesWithoutAuthenticationOrCsrf() throws Exception {
        given(idempotencyService.beginAnonymous(anyString(), anyString(), anyString()))
                .willReturn(IdempotencyResult.proceed());
        given(completeSignupUseCase.complete(any()))
                .willReturn(new CompleteSignupResult(
                        101L, "honggildong", OffsetDateTime.parse("2026-08-20T15:00:00+09:00")));

        mockMvc.perform(
                        post("/api/v1/auth/signup/complete")
                                .contextPath("/api/v1")
                                .header("Idempotency-Key", "550e8400-e29b-41d4-a716-446655440000")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {"tempSignupToken":"TEMP_SIGNUP_test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.customerId").value(101))
                .andExpect(jsonPath("$.data.userId").value("honggildong"));
    }

    @Test
    void missingIdempotencyKeyReturnsCmn0002() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/signup/complete")
                                .contextPath("/api/v1")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {"tempSignupToken":"TEMP_SIGNUP_test"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
    }

    @Test
    void blankTempSignupTokenReturnsCmn0001() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/signup/complete")
                                .contextPath("/api/v1")
                                .header("Idempotency-Key", "550e8400-e29b-41d4-a716-446655440000")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {"tempSignupToken":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    void replaysCompletedResponseWithoutExecutingSignupAgain() throws Exception {
        given(idempotencyService.beginAnonymous(anyString(), anyString(), anyString()))
                .willReturn(
                        IdempotencyResult.replay(
                                (short) 200,
                                """
                {"code":"0000","message":"회원가입이 완료되었습니다.","data":{"customerId":101,"userId":"honggildong","joinedAt":"2026-08-20T15:00:00+09:00"}}
                """));

        mockMvc.perform(
                        post("/api/v1/auth/signup/complete")
                                .contextPath("/api/v1")
                                .header("Idempotency-Key", "550e8400-e29b-41d4-a716-446655440000")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {"tempSignupToken":"TEMP_SIGNUP_test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId").value(101));

        verify(completeSignupUseCase, never()).complete(any());
        verify(idempotencyService).beginAnonymous(anyString(), anyString(), contains("TEMP_SIGNUP_test"));
    }
}
