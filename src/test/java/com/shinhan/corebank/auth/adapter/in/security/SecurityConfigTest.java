package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SecurityTestController.class)
@TestPropertySource(properties = "app.security.cors.allowed-origins=http://localhost:5173,https://www.corebank.cloud")
@Import({
    SecurityTestController.class,
    SecurityConfig.class,
    SessionAuthenticationEntryPoint.class,
    SessionAccessDeniedHandler.class,
    SessionLogoutSuccessHandler.class
})
class SecurityConfigTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("허용된 로컬 프론트엔드 Origin의 preflight 요청을 허용한다")
    void permitsPreflightRequestFromAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .contextPath("/api/v1")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(
                        result -> assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                                .containsIgnoringCase("X-XSRF-TOKEN"));
    }

    @Test
    @DisplayName("운영 프론트 Origin에서 거래 인증 헤더의 preflight 요청을 허용한다")
    void permitsTransferAuthenticationHeadersFromProductionOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/transfers")
                        .contextPath("/api/v1")
                        .header(HttpHeaders.ORIGIN, "https://www.corebank.cloud")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "content-type,x-xsrf-token,idempotency-key,"
                                        + "account-password-auth-token,otp-auth-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://www.corebank.cloud"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(result -> {
                    String allowedHeaders = result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
                    assertThat(allowedHeaders).containsIgnoringCase("X-XSRF-TOKEN");
                    assertThat(allowedHeaders).containsIgnoringCase("Idempotency-Key");
                    assertThat(allowedHeaders).containsIgnoringCase("Account-Password-Auth-Token");
                    assertThat(allowedHeaders).containsIgnoringCase("Otp-Auth-Token");
                });
    }

    @Test
    @DisplayName("설정되지 않은 Origin의 preflight 요청을 거부한다")
    void rejectsPreflightRequestFromDisallowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .contextPath("/api/v1")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("공개 API는 세션 없이 접근할 수 있다")
    void permitsPublicApiWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/products/test").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));
    }

    @Test
    @DisplayName("로그인 API는 세션과 CSRF 토큰 없이 접근할 수 있다")
    void permitsLoginWithoutSessionAndCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api/v1")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));
    }

    @ParameterizedTest(name = "{0}은 인증과 CSRF 토큰 없이 접근할 수 있다")
    @ValueSource(
            strings = {
                "/auth/terms/check",
                "/auth/verify-account",
                "/auth/check-id",
                "/auth/signup/validate",
                "/auth/signup/complete",
                "/auth/email-verifications",
                "/auth/email-verifications/email-verification-id/verify"
            })
    void permitsSignupApiWithoutSessionAndCsrfToken(String path) throws Exception {
        mockMvc.perform(post("/api/v1" + path)
                        .contextPath("/api/v1")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));
    }

    @Test
    @DisplayName("보호 API는 세션이 없으면 401 CMN0101 공통 응답을 반환한다")
    void rejectsProtectedApiWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me").contextPath("/api/v1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().encoding(StandardCharsets.UTF_8))
                .andExpect(jsonPath("$.code").value("CMN0101"))
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 세션이 만료되었습니다."))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    @Test
    @DisplayName("Actuator health는 인증 없이 접근할 수 있다")
    void permitsHealthWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/actuator/health").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("health 이외의 Actuator 경로는 공개하지 않는다")
    void protectsOtherActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/actuator/info").contextPath("/api/v1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMN0101"));
    }

    @Test
    @DisplayName("CSRF 쿠키와 헤더가 모두 없으면 403 CMN0102를 반환한다")
    void rejectsStateChangingRequestWithoutCsrfCookieAndHeader() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/v1/customers/me")
                        .contextPath("/api/v1")
                        .session(session)
                        .with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().encoding(StandardCharsets.UTF_8))
                .andExpect(jsonPath("$.code").value("CMN0102"))
                .andExpect(jsonPath("$.message").value("해당 자원에 접근할 권한이 없습니다."))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    @Test
    @DisplayName("CSRF 쿠키만 있고 헤더가 없으면 403 CMN0102를 반환한다")
    void rejectsStateChangingRequestWithCsrfCookieOnly() throws Exception {
        mockMvc.perform(post("/api/v1/customers/me")
                        .contextPath("/api/v1")
                        .cookie(new Cookie("XSRF-TOKEN", "cookie-token"))
                        .with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("CMN0102"))
                .andExpect(jsonPath("$.message").value("해당 자원에 접근할 권한이 없습니다."))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    @Test
    @DisplayName("CSRF 쿠키와 헤더 값이 다르면 403 CMN0102를 반환한다")
    void rejectsStateChangingRequestWithMismatchedCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/customers/me")
                        .contextPath("/api/v1")
                        .cookie(new Cookie("XSRF-TOKEN", "expected-token"))
                        .header("X-XSRF-TOKEN", "different-token")
                        .with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("CMN0102"))
                .andExpect(jsonPath("$.message").value("해당 자원에 접근할 권한이 없습니다."))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    @Test
    @DisplayName("동일한 CSRF 쿠키와 헤더가 있는 상태 변경 요청은 통과한다")
    void permitsStateChangingRequestWithMatchingCsrfCookieAndHeader() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/v1/customers/me")
                        .contextPath("/api/v1")
                        .session(session)
                        .with(user("customer").roles("CUSTOMER"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증된 고객은 CSRF 토큰과 함께 로그아웃할 수 있다")
    void logsOutAuthenticatedCustomer() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contextPath("/api/v1")
                        .session(session)
                        .cookie(new Cookie("XSRF-TOKEN", "issued-token"))
                        .with(csrf().asHeader())
                        .with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.message").value("로그아웃되었습니다."))
                .andExpect(jsonPath("$.data").value((Object) null))
                .andExpect(result ->
                        assertThat(result.getResponse().getCookie("JSESSIONID")).isNotNull())
                .andExpect(result -> assertThat(
                                result.getResponse().getCookie("JSESSIONID").getMaxAge())
                        .isZero())
                .andExpect(result -> assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                        .anySatisfy(cookie -> assertThat(cookie)
                                .contains("XSRF-TOKEN=")
                                .contains("Max-Age=0")
                                .contains("Path=/")))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Cache-Control"))
                        .contains("no-store"));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("로그아웃 요청에 CSRF 토큰이 없으면 403 CMN0102를 반환한다")
    void rejectsLogoutWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contextPath("/api/v1")
                        .with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));
    }

    @Test
    @DisplayName("인증 세션이 없으면 로그아웃 요청에 401 CMN0101을 반환한다")
    void rejectsLogoutWithoutSession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").contextPath("/api/v1").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMN0101"))
                .andExpect(jsonPath("$.data").value((Object) null));
    }
}
