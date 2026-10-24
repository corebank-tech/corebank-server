package com.shinhan.corebank.auth.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.auth.adapter.in.security.CsrfTokenCookieIssuer;
import com.shinhan.corebank.auth.adapter.in.security.SecurityConfig;
import com.shinhan.corebank.auth.adapter.in.security.SessionAccessDeniedHandler;
import com.shinhan.corebank.auth.adapter.in.security.SessionAuthenticationEntryPoint;
import com.shinhan.corebank.auth.adapter.in.security.SessionLoginManager;
import com.shinhan.corebank.auth.adapter.in.security.SessionLogoutSuccessHandler;
import com.shinhan.corebank.auth.application.port.in.LoginCommand;
import com.shinhan.corebank.auth.application.port.in.LoginResult;
import com.shinhan.corebank.auth.application.port.in.LoginUseCase;
import com.shinhan.corebank.auth.domain.exception.LoginFailedException;
import com.shinhan.corebank.auth.domain.model.LoginAttemptResult;
import com.shinhan.corebank.customer.api.CustomerRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminLoginController.class)
@Import({
    LoginSessionIssuer.class,
    SecurityConfig.class,
    SessionAuthenticationEntryPoint.class,
    SessionAccessDeniedHandler.class,
    SessionLogoutSuccessHandler.class
})
class AdminLoginControllerTest {

    private static final String PASSWORD = "correct-password";
    private static final String REQUEST_IP = "203.0.113.10";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    LoginUseCase loginUseCase;

    @MockitoBean
    ClientIpResolver clientIpResolver;

    @MockitoBean
    SessionLoginManager sessionLoginManager;

    @MockitoBean
    CsrfTokenCookieIssuer csrfTokenCookieIssuer;

    // 고객 로그인과 공유하는 흐름에서 이 컨트롤러만의 몫은 요구 역할을 넘기는 것이다.
    // 이 값이 비면 LoginService 의 역할 검사가 건너뛰어져 고객 계정도 관리자 경로로 들어온다.
    @Test
    @DisplayName("관리자 로그인은 ADMIN 역할을 요구하고 고객정보와 세션 만료시각을 반환한다")
    void logsInAsAdmin() throws Exception {
        LoginResult loginResult = new LoginResult(1L, "admin-user", "관리자", CustomerRole.ADMIN);

        OffsetDateTime sessionExpiresAt = OffsetDateTime.parse("2026-08-16T01:30:00Z");

        given(clientIpResolver.resolve(any(HttpServletRequest.class))).willReturn(REQUEST_IP);
        given(loginUseCase.login(new LoginCommand("admin-user", PASSWORD, REQUEST_IP, CustomerRole.ADMIN)))
                .willReturn(loginResult);
        given(sessionLoginManager.establishSession(
                        any(LoginResult.class), any(HttpServletRequest.class), any(HttpServletResponse.class)))
                .willReturn(sessionExpiresAt);

        mockMvc.perform(
                        post("/api/v1/admin/auth/login")
                                .contextPath("/api/v1")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "userId": "admin-user",
                                  "password": "correct-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.customerId").value(1L))
                .andExpect(jsonPath("$.data.userName").value("관리자"))
                .andExpect(jsonPath("$.data.sessionExpiresAt").value("2026-08-16T01:30:00Z"));

        verify(loginUseCase).login(new LoginCommand("admin-user", PASSWORD, REQUEST_IP, CustomerRole.ADMIN));
        verify(csrfTokenCookieIssuer).rotate(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    // 고객 계정이 관리자 경로에 오면 비밀번호 불일치와 같은 ATH0101 이고 세션도 발급되지 않는다
    // (admin_auth.md §1 ③ · REQ-NFR-017).
    @Test
    @DisplayName("고객 계정의 관리자 로그인은 ATH0101을 주고 세션을 발급하지 않는다")
    void rejectsCustomerAccount() throws Exception {
        given(clientIpResolver.resolve(any(HttpServletRequest.class))).willReturn(REQUEST_IP);
        given(loginUseCase.login(any(LoginCommand.class)))
                .willThrow(LoginFailedException.invalidCredentials(new LoginAttemptResult(1, 4)));

        mockMvc.perform(
                        post("/api/v1/admin/auth/login")
                                .contextPath("/api/v1")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "userId": "customer-user",
                                  "password": "correct-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ATH0101"))
                .andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.remainingAttempts").value(4));

        verify(sessionLoginManager, never())
                .establishSession(
                        any(LoginResult.class), any(HttpServletRequest.class), any(HttpServletResponse.class));
        verify(csrfTokenCookieIssuer, never()).rotate(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }
}
