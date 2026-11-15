package com.shinhan.corebank.subscription.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.subscription.api.TerminationPreviewUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.subscription.early-termination.enabled=false")
@DisplayName("중도해지 예상 조회 비활성화 테스트")
class EarlyTerminationPreviewDisabledTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @MockitoBean
    private TerminationPreviewUseCase previewUseCase;

    @Test
    @DisplayName("기능 비활성화 시 예상 조회 엔드포인트가 등록되지 않는다")
    void preview_disabled() throws Exception {
        boolean registered = handlerMapping.getHandlerMethods().keySet().stream()
                .anyMatch(info -> info.toString().contains("/early-termination/preview"));

        assertThat(registered).isFalse();

        AuthenticatedCustomer customer = new AuthenticatedCustomer(7L, "user7", "테스터");

        var auth = UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));

        mockMvc.perform(get("/accounts/101/early-termination/preview").with(authentication(auth)))
                .andExpect(status().isNotFound());

        verifyNoInteractions(previewUseCase);
    }
}
