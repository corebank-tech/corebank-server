package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.customer.api.CustomerRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// 리스너가 호출되는지는 LogoutAuditEventListenerTest 가 본다. 여기서는 그 뒤 — 실제 MySQL 에 남는지를 본다.
@DisplayName("로그아웃 감사 로그 MySQL 통합 테스트")
class LogoutAuditPersistenceIntegrationTest extends IntegrationTestSupport {

    private static final String USER_ID = "logout-audit-user";
    private static final String EMAIL = "logout-audit@example.com";
    private static final String REQUEST_IP = "192.168.0.11";

    @Autowired
    private LogoutAuditEventListener logoutAuditEventListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long customerId;

    @BeforeEach
    void setUp() {
        deleteTestData();
        customerId = insertCustomer();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        deleteTestData();
    }

    // 관리자·고객 어느 쪽이든 역할이 detail 에 남아야 누가 끊었는지 추적된다 (tasks.md PH-49a-②).
    @ParameterizedTest(name = "{0} 로그아웃은 LOGOUT 성공 감사로 저장된다")
    @EnumSource(CustomerRole.class)
    @DisplayName("로그아웃을 역할과 함께 LOGOUT 성공 감사로 저장한다")
    void persistsLogoutAudit(CustomerRole role) {
        publishLogoutSuccess(role);

        PersistedAudit audit = findLatestAudit();

        assertThat(audit.eventType()).isEqualTo("LOGOUT");
        assertThat(audit.result()).isEqualTo("SUCCESS");
        assertThat(audit.requestIp()).isEqualTo(REQUEST_IP);
        assertThat(audit.role()).isEqualTo(role.name());
        assertThat(audit.transactionNumber()).isNull();
    }

    @Test
    @DisplayName("감사 detail 에 세션 식별자나 비밀번호를 남기지 않는다")
    void doesNotRecordSensitiveData() {
        publishLogoutSuccess(CustomerRole.CUSTOMER);

        String detail = findLatestAudit().detail().toLowerCase();

        assertThat(detail)
                .doesNotContain("password")
                .doesNotContain("sessionid")
                .doesNotContain("session_id");
    }

    // 리스너는 세션 authority 로 역할을 읽고 요청에서 IP 를 꺼내므로, 둘을 로그인 때와 같은 모양으로 세워 둔다.
    private void publishLogoutSuccess(CustomerRole role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(REQUEST_IP);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(customerId, USER_ID, "감사테스트"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_" + role.name()));

        logoutAuditEventListener.onLogoutSuccess(new LogoutSuccessEvent(authentication));
    }

    private Long insertCustomer() {
        jdbcTemplate.update(
                """
                INSERT INTO customer (
                    user_id, password_hash, user_name, birth_date, email, phone_number,
                    joined_at, created_at, updated_at
                ) VALUES (?, 'not-a-login-test', ?, ?, ?, ?, NOW(6), NOW(6), NOW(6))
                """,
                USER_ID,
                "감사테스트",
                "1990-01-01",
                EMAIL,
                "01012345678");

        return jdbcTemplate.queryForObject("SELECT customer_id FROM customer WHERE user_id = ?", Long.class, USER_ID);
    }

    private PersistedAudit findLatestAudit() {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    event_type,
                    result,
                    request_ip,
                    transaction_number,
                    JSON_UNQUOTE(JSON_EXTRACT(detail, '$.role')) AS role,
                    CAST(detail AS CHAR) AS detail
                FROM audit_log
                WHERE customer_id = ?
                ORDER BY audit_log_id DESC
                LIMIT 1
                """,
                (resultSet, rowNumber) -> new PersistedAudit(
                        resultSet.getString("event_type"),
                        resultSet.getString("result"),
                        resultSet.getString("request_ip"),
                        resultSet.getString("transaction_number"),
                        resultSet.getString("role"),
                        resultSet.getString("detail")),
                customerId);
    }

    private void deleteTestData() {
        jdbcTemplate.update(
                """
                DELETE FROM audit_log
                WHERE customer_id IN (SELECT customer_id FROM customer WHERE user_id = ?)
                """,
                USER_ID);
        jdbcTemplate.update("DELETE FROM customer WHERE user_id = ?", USER_ID);
    }

    // 로그아웃 감사의 저장 결과
    private record PersistedAudit(
            String eventType, String result, String requestIp, String transactionNumber, String role, String detail) {}
}
