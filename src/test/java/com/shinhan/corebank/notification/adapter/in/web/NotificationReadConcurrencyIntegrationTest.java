package com.shinhan.corebank.notification.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.notification.application.port.out.NotificationCommandPort;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

// 트랜잭션을 실제로 나눠야 경합이 나므로 테스트 트랜잭션 없이 커밋된 데이터로 검증한다.
@AutoConfigureMockMvc
class NotificationReadConcurrencyIntegrationTest extends IntegrationTestSupport {

    private static final int CONCURRENT_REQUESTS = 4;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final long RIVAL_COMMIT_DELAY_MILLIS = 300;

    @Autowired
    MockMvc mockMvc;

    @MockitoSpyBean
    NotificationCommandPort notificationCommandPort;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DataSource dataSource;

    @Autowired
    CustomerTestFixture customerFixture;

    @Autowired
    ObjectMapper objectMapper;

    private Long customerId;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM notification WHERE customer_id = ?", customerId);
        jdbcTemplate.update("DELETE FROM idempotency_key WHERE customer_id = ?", customerId);
        customerFixture.deleteCustomer(customerId);
    }

    @RepeatedTest(3)
    @DisplayName("같은 알림을 서로 다른 키로 동시에 읽어도 모두 200이고 응답과 DB의 읽음 시각이 하나로 같다")
    void markAsRead_concurrentRequests_keepSingleFirstReadAt() throws Exception {
        customerId = customerFixture.createCustomer();
        final Long notificationId = insertUnread();

        final List<MockHttpServletResponse> responses = invokeConcurrently(notificationId);

        final LocalDateTime savedReadAt = jdbcTemplate.queryForObject(
                "SELECT read_at FROM notification WHERE notification_id = ?", LocalDateTime.class, notificationId);
        assertThat(savedReadAt).isNotNull();
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(readAtOf(response)).isEqualTo(savedReadAt);
        });
    }

    @Test
    @DisplayName("경합 상대가 행을 잠근 채 커밋 전이면 기다렸다가 상대의 읽음 시각을 응답하고 덮어쓰지 않는다")
    void markAsRead_rivalHoldsRowLock_waitsAndReturnsRivalReadAt() throws Exception {
        customerId = customerFixture.createCustomer();
        final Long notificationId = insertUnread();
        final LocalDateTime rivalReadAt = LocalDateTime.of(2026, 11, 7, 9, 0, 0, 123_456_000);
        final CountDownLatch requestReading = new CountDownLatch(1);
        doAnswer(invocation -> {
                    requestReading.countDown();
                    return invocation.callRealMethod();
                })
                .when(notificationCommandPort)
                .findByIdForUpdate(any());

        final ExecutorService requestExecutor = Executors.newSingleThreadExecutor();
        try (Connection rival = dataSource.getConnection()) {
            rival.setAutoCommit(false);
            updateReadAt(rival, notificationId, rivalReadAt);
            final Future<MockHttpServletResponse> request =
                    requestExecutor.submit(() -> performMarkAsRead(notificationId));
            assertThat(requestReading.await(10, TimeUnit.SECONDS)).isTrue();
            // 요청이 행을 읽으러 들어간 뒤에 커밋해야, 잠금 없이 읽는 구현이면 미읽음을 보고 덮어쓴다
            Thread.sleep(RIVAL_COMMIT_DELAY_MILLIS);
            rival.commit();

            final MockHttpServletResponse response = request.get(30, TimeUnit.SECONDS);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(readAtOf(response)).isEqualTo(rivalReadAt);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT read_at FROM notification WHERE notification_id = ?",
                            LocalDateTime.class,
                            notificationId))
                    .isEqualTo(rivalReadAt);
        } finally {
            requestExecutor.shutdownNow();
        }
    }

    private void updateReadAt(Connection connection, Long notificationId, LocalDateTime readAt) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE notification SET is_read = TRUE, read_at = ? WHERE notification_id = ?")) {
            statement.setTimestamp(1, Timestamp.valueOf(readAt));
            statement.setLong(2, notificationId);
            statement.executeUpdate();
        }
    }

    private MockHttpServletResponse performMarkAsRead(Long notificationId) throws Exception {
        return mockMvc.perform(patch("/notifications/{notificationId}/read", notificationId)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(authentication(customerAuthentication())))
                .andReturn()
                .getResponse();
    }

    private List<MockHttpServletResponse> invokeConcurrently(Long notificationId) throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        final CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        final CountDownLatch start = new CountDownLatch(1);
        try {
            final List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (int index = 0; index < CONCURRENT_REQUESTS; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return performMarkAsRead(notificationId);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            final List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }

    private LocalDateTime readAtOf(MockHttpServletResponse response) throws Exception {
        final String readAt = objectMapper
                .readTree(response.getContentAsString())
                .path("data")
                .path("readAt")
                .asString();
        return OffsetDateTime.parse(readAt).atZoneSameInstant(KOREA_ZONE).toLocalDateTime();
    }

    private Long insertUnread() {
        jdbcTemplate.update(
                """
                INSERT INTO notification (customer_id, type, title, content, is_read, occurred_at)
                VALUES (?, 'TRANSFER', '알림 제목', '알림 본문', FALSE, ?)
                """,
                customerId,
                Timestamp.valueOf(LocalDateTime.of(2026, 11, 7, 10, 0)));
        return jdbcTemplate.queryForObject(
                "SELECT MAX(notification_id) FROM notification WHERE customer_id = ?", Long.class, customerId);
    }

    private UsernamePasswordAuthenticationToken customerAuthentication() {
        final AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
