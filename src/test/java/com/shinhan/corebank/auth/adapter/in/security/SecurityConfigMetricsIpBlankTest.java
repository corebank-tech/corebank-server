package com.shinhan.corebank.auth.adapter.in.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.customer.api.AdminPermissionFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SecurityTestController.class)
@TestPropertySource(
        properties = {"app.security.cors.allowed-origins=http://localhost:5173", "app.security.metrics-allowed-ip="})
@Import({
    SecurityTestController.class,
    SecurityConfig.class,
    SessionAuthenticationEntryPoint.class,
    SessionAccessDeniedHandler.class,
    SessionLogoutSuccessHandler.class
})
class SecurityConfigMetricsIpBlankTest {

    @MockitoBean
    AdminPermissionFacade adminPermissionFacade;

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("허용 IP를 비워 두면 어느 주소에서도 Prometheus 지표를 읽을 수 없다")
    void rejectsPrometheusFromAnyIpWhenAllowedIpIsBlank() throws Exception {
        mockMvc.perform(get("/api/v1/actuator/prometheus")
                        .contextPath("/api/v1")
                        .with(request -> {
                            request.setRemoteAddr("192.168.0.211");
                            return request;
                        }))
                .andExpect(status().isUnauthorized());
    }
}
