package com.shinhan.corebank.paymentstop.adapter.in.web.internal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.customer.api.AdminPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;

/** 내부 API 는 세션이 아니라 워커 키와 작업 토큰으로만 열린다. CSRF 검사 대상도 아니다(워커는 쿠키를 쓰지 않는다). */
@AutoConfigureMockMvc
@DisplayName("이의제기 Agent 내부 API 인증")
class AgentInternalControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.payment-stop.agent.worker-key}")
    private String workerKey;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 시드와 다른 테스트가 쓰지 않는 관리자 ID. 세션 행 정리에 쓴다
    private static final long ADMIN_ID = 9_916_005L;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", String.valueOf(ADMIN_ID));
    }

    @Test
    @DisplayName("워커 키 없이 작업을 가져가려 하면 CSRF 토큰 없이도 401 PST0101이다")
    void claimWithoutKey() throws Exception {
        mockMvc.perform(post("/internal/review-jobs/claim"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PST0101"));
    }

    @Test
    @DisplayName("워커 키가 맞으면 CSRF 토큰 없이 작업 가져가기가 통과한다")
    void claimWithKey() throws Exception {
        mockMvc.perform(post("/internal/review-jobs/claim").header(AgentInternalController.WORKER_KEY, workerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));
    }

    @Test
    @DisplayName("모든 권한을 가진 관리자 세션이라도 작업 토큰이 없으면 403 PST0102다")
    void adminSessionCannotUseInternalApi() throws Exception {
        mockMvc.perform(get("/internal/objections/{id}/context", 1L)
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                new AuthenticatedCustomer(ADMIN_ID, "admin", "관리자"),
                                null,
                                AuthorityUtils.createAuthorityList(
                                        "ROLE_ADMIN", AdminPermission.PAYMENT_STOP_READ.name())))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PST0102"));
    }
}
