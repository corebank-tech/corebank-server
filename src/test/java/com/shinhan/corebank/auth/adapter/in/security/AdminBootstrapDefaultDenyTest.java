package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 허용 목록을 덮어쓰지 않고 application.yml의 기본값(${ADMIN_BOOTSTRAP_CUSTOMER_IDS:})을 그대로 탄다.
 * 환경변수가 없는 prod와 같은 조건에서 /admin/**가 전부 닫히는지 확인한다.
 */
@WebMvcTest(controllers = SecurityTestController.class)
@Import({
    SecurityTestController.class,
    SecurityConfig.class,
    SessionAuthenticationEntryPoint.class,
    SessionAccessDeniedHandler.class,
    SessionLogoutSuccessHandler.class
})
class AdminBootstrapDefaultDenyTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AdminBootstrapProperties properties;

    @Test
    @DisplayName("환경변수가 없으면 허용 목록이 비어 있고 로그인한 고객도 관리자 API에서 403 CMN0102를 받는다")
    void deniesAdminApiWhenAllowlistIsNotConfigured() throws Exception {
        assertThat(properties.bootstrapCustomerIds()).isEmpty();

        mockMvc.perform(get("/api/v1/admin/test")
                        .contextPath("/api/v1")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                new AuthenticatedCustomer(1L, "user1", "테스터"),
                                null,
                                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));
    }
}
