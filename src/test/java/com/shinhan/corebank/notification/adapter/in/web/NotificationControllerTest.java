package com.shinhan.corebank.notification.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class NotificationControllerTest extends IntegrationTestSupport {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 7, 10, 0);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    CustomerTestFixture customerFixture;

    private final List<Long> createdCustomerIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long customerId : createdCustomerIds) {
            jdbcTemplate.update("DELETE FROM notification WHERE customer_id = ?", customerId);
            jdbcTemplate.update("DELETE FROM idempotency_key WHERE customer_id = ?", customerId);
            customerFixture.deleteCustomer(customerId);
        }
    }

    @Test
    @DisplayName("미읽음 건수는 읽은 알림과 다른 고객 알림을 빼고 센다")
    void getUnreadCount_readAndOtherCustomer_areExcluded() throws Exception {
        final Long customerId = createCustomer();
        insertUnread(customerId, "TRANSFER", BASE_TIME);
        insertUnread(customerId, "AUTO_TRANSFER", BASE_TIME.plusMinutes(1));
        insertRead(customerId, BASE_TIME.plusMinutes(2));
        insertUnread(createCustomer(), "TRANSFER", BASE_TIME);

        mockMvc.perform(get("/notifications/unread-count").with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.unreadCount").value(2));
    }

    @Test
    @DisplayName("알림이 없으면 미읽음 건수는 0이다")
    void getUnreadCount_noNotification_returnsZero() throws Exception {
        mockMvc.perform(get("/notifications/unread-count").with(authentication(authenticationOf(createCustomer()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    @DisplayName("인증 없이 미읽음 건수를 요청하면 401 CMN0101이다")
    void getUnreadCount_withoutAuthentication_returnsCmn0101() throws Exception {
        mockMvc.perform(get("/notifications/unread-count"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMN0101"));
    }

    private Long createCustomer() {
        final Long customerId = customerFixture.createCustomer();
        createdCustomerIds.add(customerId);
        return customerId;
    }

    private Long insertUnread(Long customerId, String type, LocalDateTime occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO notification (customer_id, type, title, content, is_read, occurred_at)
                VALUES (?, ?, '알림 제목', '알림 본문', FALSE, ?)
                """,
                customerId,
                type,
                Timestamp.valueOf(occurredAt));
        return lastInsertedNotificationId(customerId);
    }

    private Long insertRead(Long customerId, LocalDateTime occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO notification (customer_id, type, title, content, is_read, read_at, occurred_at)
                VALUES (?, 'TRANSFER', '알림 제목', '알림 본문', TRUE, '2026-11-07 09:00:00.123456', ?)
                """,
                customerId,
                Timestamp.valueOf(occurredAt));
        return lastInsertedNotificationId(customerId);
    }

    private Long lastInsertedNotificationId(Long customerId) {
        return jdbcTemplate.queryForObject(
                "SELECT MAX(notification_id) FROM notification WHERE customer_id = ?", Long.class, customerId);
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        final AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
