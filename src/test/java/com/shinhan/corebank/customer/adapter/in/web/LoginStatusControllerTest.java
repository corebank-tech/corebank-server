package com.shinhan.corebank.customer.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogJpaEntity;
import com.shinhan.corebank.common.audit.AuditLogJpaRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class LoginStatusControllerTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuditLogJpaRepository auditLogJpaRepository;

    @Test
    @DisplayName("직전 로그인 기록이 있으면 그 일시·IP를 응답으로 반환한다")
    void getLoginStatus_previousLoginExists_returnsItsInfo() throws Exception {
        Long customerId = 1001L;
        auditLogJpaRepository.save(AuditLogJpaEntity.of(customerId, null, AuditEventType.LOGIN,
                "1.1.1.1", true, null, LocalDateTime.of(2026, 3, 1, 9, 0)));
        auditLogJpaRepository.save(AuditLogJpaEntity.of(customerId, null, AuditEventType.LOGIN,
                "2.2.2.2", true, null, LocalDateTime.of(2026, 3, 5, 10, 0)));
        auditLogJpaRepository.save(AuditLogJpaEntity.of(customerId, null, AuditEventType.LOGIN,
                "3.3.3.3", true, null, LocalDateTime.of(2026, 3, 10, 11, 0))); // 이번 로그인

        mockMvc.perform(get("/dashboard/login-status")
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.previousLoginAt").value("2026-03-05T10:00:00"))
                .andExpect(jsonPath("$.data.previousLoginIp").value("2.2.2.2"));
    }

    @Test
    @DisplayName("직전 로그인 기록이 없으면(첫 로그인) null 필드로 응답한다")
    void getLoginStatus_noPreviousLogin_returnsNullFields() throws Exception {
        Long customerId = 1002L;
        auditLogJpaRepository.save(AuditLogJpaEntity.of(customerId, null, AuditEventType.LOGIN,
                "1.1.1.1", true, null, LocalDateTime.of(2026, 3, 10, 9, 0))); // 이번 로그인뿐

        mockMvc.perform(get("/dashboard/login-status")
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.previousLoginAt").doesNotExist())
                .andExpect(jsonPath("$.data.previousLoginIp").doesNotExist());
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환한다")
    void getLoginStatus_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/dashboard/login-status"))
                .andExpect(status().isUnauthorized());
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
