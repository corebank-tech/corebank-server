package com.shinhan.corebank.auth.adapter.in.security;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SecurityTestController.class)
@Import({
        SecurityConfig.class,
        SessionAuthenticationEntryPoint.class,
        SessionAccessDeniedHandler.class
})
class SecurityConfigTest {

    @Autowired
    MockMvc mockMvc;

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

    @Test
    @DisplayName("보호 API는 세션이 없으면 401 CMN0101 공통 응답을 반환한다")
    void rejectsProtectedApiWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me").contextPath("/api/v1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().encoding(StandardCharsets.UTF_8))
                .andExpect(jsonPath("$.code").value("CMN0101"))
                .andExpect(jsonPath("$.message")
                        .value("인증정보가 없거나 세션이 만료되었습니다."))
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
    @DisplayName("CSRF 토큰이 없는 상태 변경 요청은 403으로 차단한다")
    void rejectsStateChangingRequestWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/customers/me")
                        .contextPath("/api/v1")
                        .with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));
    }

    @Test
    @DisplayName("인증정보와 CSRF 토큰이 있는 상태 변경 요청은 통과한다")
    void permitsStateChangingRequestWithCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/customers/me")
                        .contextPath("/api/v1")
                        .with(user("customer").roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

}
