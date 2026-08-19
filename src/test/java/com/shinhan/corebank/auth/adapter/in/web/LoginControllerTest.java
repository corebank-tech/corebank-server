package com.shinhan.corebank.auth.adapter.in.web;

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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LoginController.class)
@Import({
        SecurityConfig.class,
        SessionAuthenticationEntryPoint.class,
        SessionAccessDeniedHandler.class,
        SessionLogoutSuccessHandler.class
})
class LoginControllerTest {

    private static final String PASSWORD = "correct-password";
    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

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

    @Test
    @DisplayName("로그인 성공 시 고객정보와 세션 만료시각을 반환한다")
    void logsInSuccessfully() throws Exception {
        LoginResult loginResult =
                new LoginResult(1L, "login-user", "홍길동");

        OffsetDateTime sessionExpiresAt =
                OffsetDateTime.parse("2026-08-16T01:10:00Z");

        given(clientIpResolver.resolve(any(HttpServletRequest.class)))
                .willReturn("203.0.113.10");
        given(loginUseCase.login(new LoginCommand(
                "login-user",
                PASSWORD,
                "203.0.113.10"
        ))).willReturn(loginResult);
        given(sessionLoginManager.establishSession(
                any(LoginResult.class),
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        )).willReturn(sessionExpiresAt);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api/v1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "login-user",
                                  "password": "correct-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.customerId").value(1L))
                .andExpect(jsonPath("$.data.userName").value("홍길동"))
                .andExpect(jsonPath("$.data.sessionExpiresAt")
                        .value("2026-08-16T01:10:00Z"))
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andExpect(content().string(not(containsString(PASSWORD_HASH))));

        verify(sessionLoginManager).establishSession(
                any(LoginResult.class),
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
        verify(csrfTokenCookieIssuer).rotate(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    @Test
    @DisplayName("존재하지 않는 아이디는 ATH0101과 빈 데이터를 반환한다")
    void returnsLoginFailureWithoutAttemptData() throws Exception {
        given(clientIpResolver.resolve(any(HttpServletRequest.class)))
                .willReturn("203.0.113.10");
        given(loginUseCase.login(any(LoginCommand.class)))
                .willThrow(LoginFailedException.customerNotFound());

        performLogin()
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ATH0101"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(sessionLoginManager, never()).establishSession(
                any(LoginResult.class),
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
        verify(csrfTokenCookieIssuer, never()).rotate(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    @Test
    @DisplayName("비밀번호 불일치는 ATH0101과 시도 횟수를 반환한다")
    void returnsLoginFailureWithAttemptData() throws Exception {
        given(clientIpResolver.resolve(any(HttpServletRequest.class)))
                .willReturn("203.0.113.10");
        given(loginUseCase.login(any(LoginCommand.class)))
                .willThrow(LoginFailedException.invalidCredentials(
                        new LoginAttemptResult(2, 3)
                ));

        performLogin()
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ATH0101"))
                .andExpect(jsonPath("$.data.errorCount").value(2))
                .andExpect(jsonPath("$.data.remainingAttempts").value(3));

        verify(sessionLoginManager, never()).establishSession(
                any(LoginResult.class),
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    @Test
    @DisplayName("잠긴 계정은 ATH0102와 빈 데이터를 반환한다")
    void returnsAccountLocked() throws Exception {
        given(clientIpResolver.resolve(any(HttpServletRequest.class)))
                .willReturn("203.0.113.10");
        given(loginUseCase.login(any(LoginCommand.class)))
                .willThrow(LoginFailedException.accountLocked());

        performLogin()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ATH0102"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(sessionLoginManager, never()).establishSession(
                any(LoginResult.class),
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    @Test
    @DisplayName("필수 입력이 누락되면 CMN0001을 반환한다")
    void rejectsMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api/v1")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));

        verify(clientIpResolver, never())
                .resolve(any(HttpServletRequest.class));
        verify(loginUseCase, never())
                .login(any(LoginCommand.class));
    }

    private org.springframework.test.web.servlet.ResultActions performLogin()
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contextPath("/api/v1")
                .contentType(APPLICATION_JSON)
                .content("""
                        {
                          "userId": "login-user",
                          "password": "wrong-password"
                        }
                        """));
    }
}
