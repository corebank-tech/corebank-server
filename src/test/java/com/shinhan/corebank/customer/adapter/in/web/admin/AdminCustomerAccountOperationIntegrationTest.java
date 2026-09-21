package com.shinhan.corebank.customer.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * #449 DoD — 관리자 API로 푼 계정·초기화한 계정에 실제 /auth/login으로 로그인되는지, 감사가 실제로 커밋되는지 본다.
 *
 * <p>테스트 트랜잭션을 쓰지 않는다. 실패 감사는 AuditLogService가 별도 트랜잭션으로 커밋하는데, 테스트 트랜잭션
 * 안에서는 그 커밋을 확인할 수 없기 때문이다. 대신 넣은 행을 @AfterEach에서 지운다.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.security.admin.bootstrap-customer-ids=" + AdminCustomerControllerTest.ADMIN_ID)
@DisplayName("관리자 고객 계정 운영 DoD 통합 테스트")
class AdminCustomerAccountOperationIntegrationTest extends IntegrationTestSupport {

    private static final long ADMIN_ID = AdminCustomerControllerTest.ADMIN_ID;
    private static final String TARGET_USER_ID = "adm449target";
    private static final String ORIGINAL_PASSWORD = "Origin!449pass";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Long targetId;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertCustomer(ADMIN_ID, "adm449admin", "admin@adm449.test", 0, false);
        targetId = insertCustomer(null, TARGET_USER_ID, "target@adm449.test", 5, true);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM audit_log WHERE customer_id = ? OR customer_id IN "
                        + "(SELECT customer_id FROM customer WHERE user_id LIKE 'adm449%')",
                ADMIN_ID);
        jdbcTemplate.update("DELETE FROM idempotency_key WHERE customer_id = ?", ADMIN_ID);
        jdbcTemplate.update("DELETE FROM customer WHERE user_id LIKE 'adm449%' OR customer_id = ?", ADMIN_ID);
    }

    @Test
    @DisplayName("잠긴 고객을 관리자가 해제하면 원래 비밀번호로 로그인할 수 있고 감사가 1행 남는다")
    void unlockedCustomerCanLogIn() throws Exception {
        login(TARGET_USER_ID, ORIGINAL_PASSWORD).andExpect(jsonPath("$.code").value("ATH0102"));

        mockMvc.perform(post("/admin/customers/{id}/unlock", targetId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isOk());

        login(TARGET_USER_ID, ORIGINAL_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));
        assertThat(adminAuditRows("ACCOUNT_UNLOCK", "SUCCESS")).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자가 초기화한 임시 비밀번호로 잠겨 있던 계정에 로그인할 수 있고 옛 비밀번호는 거부된다")
    void resetCustomerCanLogInWithTemporaryPassword() throws Exception {
        String body = mockMvc.perform(post("/admin/customers/{id}/password-reset", targetId)
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String temporaryPassword = JsonPath.read(body, "$.data.temporaryPassword");

        login(TARGET_USER_ID, ORIGINAL_PASSWORD).andExpect(status().is4xxClientError());
        login(TARGET_USER_ID, temporaryPassword)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));

        assertThat(adminAuditRows("PASSWORD_RESET_BY_ADMIN", "SUCCESS")).isEqualTo(1);
        String detail = jdbcTemplate.queryForObject(
                "SELECT CAST(detail AS CHAR) FROM audit_log WHERE customer_id = ? AND event_type = 'PASSWORD_RESET_BY_ADMIN'",
                String.class,
                ADMIN_ID);
        assertThat(detail).doesNotContain(temporaryPassword).contains("\"passwordChanged\": true");
    }

    @Test
    @DisplayName("없는 고객과 자기 자신 대상은 거부되고, 업무가 롤백돼도 실패 감사는 커밋된다")
    void failedRequestsAreAuditedEvenWhenRolledBack() throws Exception {
        mockMvc.perform(post("/admin/customers/{id}/password-reset", 999_999_999L)
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATH0201"));
        mockMvc.perform(post("/admin/customers/{id}/password-reset", ADMIN_ID)
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE customer_id = ? AND event_type = 'PASSWORD_RESET_BY_ADMIN' "
                                + "AND result = 'FAILURE' AND JSON_UNQUOTE(JSON_EXTRACT(detail, '$.reason')) "
                                + "IN ('CUSTOMER_NOT_FOUND', 'SELF_TARGET')",
                        Integer.class,
                        ADMIN_ID))
                .isEqualTo(2);
        assertThat(adminAuditRows("PASSWORD_RESET_BY_ADMIN", "SUCCESS")).isZero();
    }

    // MockHttpSession의 만료시간 기본값은 0이라 SessionLoginManager가 거부한다 — 운영과 같은 10분(POL-001)을 준다.
    private ResultActions login(String userId, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setMaxInactiveInterval(600);
        return mockMvc.perform(post("/auth/login")
                .session(session)
                .contentType("application/json")
                .content("{\"userId\":\"%s\",\"password\":\"%s\"}".formatted(userId, password)));
    }

    private int adminAuditRows(String eventType, String result) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE customer_id = ? AND event_type = ? AND result = ? "
                        + "AND JSON_EXTRACT(detail, '$.targetCustomerId') = ?",
                Integer.class,
                ADMIN_ID,
                eventType,
                result,
                targetId);
    }

    private RequestPostProcessor admin() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(ADMIN_ID, "adm449admin", "관리자"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));
    }

    private Long insertCustomer(Long customerId, String userId, String email, int failures, boolean locked) {
        jdbcTemplate.update(
                "INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number, "
                        + "login_failure_count, account_locked, joined_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, '홍길동', '1990-01-01', ?, '01012345678', ?, ?, NOW(6), NOW(6), NOW(6))",
                customerId,
                userId,
                passwordEncoder.encode(ORIGINAL_PASSWORD),
                email,
                failures,
                locked);
        return jdbcTemplate.queryForObject("SELECT customer_id FROM customer WHERE user_id = ?", Long.class, userId);
    }
}
