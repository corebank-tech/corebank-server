package com.shinhan.corebank.auth.adapter.in.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// CorrelationIdFilter가 Security 체인보다 먼저 동작해 401 응답에도 헤더를 싣는지 확인한다(PR #560 리뷰)
@WebMvcTest(controllers = SecurityTestController.class)
@TestPropertySource(
        properties = {
            "app.security.cors.allowed-origins=http://localhost:5173,https://www.corebank.cloud",
            "app.security.admin.bootstrap-customer-ids=7"
        })
@Import({
    SecurityTestController.class,
    SecurityConfig.class,
    SessionAuthenticationEntryPoint.class,
    SessionAccessDeniedHandler.class,
    SessionLogoutSuccessHandler.class
})
class CorrelationIdSecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("세션 없이 보호 API를 호출해 401이 나와도 X-Correlation-Id 응답 헤더가 있다")
    void unauthorizedResponse_stillHasCorrelationIdHeader() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me").contextPath("/api/v1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    @DisplayName("요청에 X-Correlation-Id를 실어 보내면 401 응답에도 같은 값이 그대로 돌아온다")
    void unauthorizedResponse_echoesClientProvidedCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me")
                        .contextPath("/api/v1")
                        .header("X-Correlation-Id", "client-fixed-id"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "client-fixed-id"));
    }
}
