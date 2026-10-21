package com.shinhan.corebank.auth.adapter.in.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.auth.adapter.in.web.ClientIpResolver;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.customer.api.AdminPermission;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 로그아웃 감사가 Spring Security 의 LogoutSuccessEvent 로 실제 호출되는지, 그때 요청에서 IP 를 꺼낼 수 있는지 본다.
@WebMvcTest(controllers = SecurityTestController.class)
@Import({
    SecurityTestController.class,
    SecurityConfig.class,
    SessionAuthenticationEntryPoint.class,
    SessionAccessDeniedHandler.class,
    SessionLogoutSuccessHandler.class,
    LogoutAuditEventListener.class,
    ClientIpResolver.class
})
class LogoutAuditEventListenerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuditLogService auditLogService;

    @Test
    @DisplayName("관리자 로그아웃을 요청 IP와 ADMIN 역할로 감사에 남긴다")
    void recordsAdminLogout() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .session(new MockHttpSession())
                        .remoteAddress("10.0.0.7")
                        .with(authentication(admin()))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(auditLogService).record(7L, null, AuditEventType.LOGOUT, "10.0.0.7", true, Map.of("role", "ADMIN"));
    }

    @Test
    @DisplayName("고객 로그아웃은 CUSTOMER 역할로 남긴다")
    void recordsCustomerLogout() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .session(new MockHttpSession())
                        .with(authentication(customer()))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(auditLogService).record(1L, null, AuditEventType.LOGOUT, "127.0.0.1", true, Map.of("role", "CUSTOMER"));
    }

    // 세션은 이미 끊긴 뒤라 감사 저장이 실패해도 사용자는 로그아웃 응답을 받아야 한다.
    @Test
    @DisplayName("감사 저장이 실패해도 로그아웃 응답은 200 이다")
    void keepsLogoutResponseWhenAuditFails() throws Exception {
        doThrow(new IllegalStateException("감사 저장 실패"))
                .when(auditLogService)
                .record(anyLong(), any(), any(AuditEventType.class), anyString(), anyBoolean(), anyMap());

        mockMvc.perform(post("/auth/logout")
                        .session(new MockHttpSession())
                        .with(authentication(customer()))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    private UsernamePasswordAuthenticationToken admin() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(7L, "admin7", "관리자"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_ADMIN", AdminPermission.CUSTOMER_READ.name()));
    }

    private UsernamePasswordAuthenticationToken customer() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(1L, "user01", "홍길동"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
