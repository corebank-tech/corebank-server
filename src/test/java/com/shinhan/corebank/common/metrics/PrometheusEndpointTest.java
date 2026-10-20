package com.shinhan.corebank.common.metrics;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// @SpringBootTest는 지표 내보내기를 꺼 두므로 Prometheus 내보내기를 직접 켠다. 이 설정이 달라 컨텍스트를 따로 띄운다.
@AutoConfigureMockMvc
@TestPropertySource(properties = "management.prometheus.metrics.export.enabled=true")
@DisplayName("/actuator/prometheus 메트릭 노출")
class PrometheusEndpointTest extends IntegrationTestSupport {

    private static final String URL = "/actuator/prometheus";

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("로그인 없이 부르면 401이다 — prod에서 열기 전까지 세션 인증 뒤에 둔다")
    void anonymous_unauthorized() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("이체·COB가 한 번도 안 돌았어도 transfer·cob 메트릭과 이체 histogram 구간이 나온다 (#541)")
    void authenticated_exposesTransferAndCobMetrics() throws Exception {
        mockMvc.perform(get(URL).with(customer()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("transfer_count_total")))
                .andExpect(content().string(Matchers.containsString("transfer_duration_seconds_bucket")))
                .andExpect(content().string(Matchers.containsString("cob_step_count_total")))
                .andExpect(content().string(Matchers.containsString("cob_step_duration_seconds_count")));
    }

    private RequestPostProcessor customer() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(944910001L, "metrics-test", "메트릭"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));
    }
}
