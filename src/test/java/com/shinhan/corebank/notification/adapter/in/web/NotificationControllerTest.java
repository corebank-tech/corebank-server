package com.shinhan.corebank.notification.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.idempotency.IdempotencyFingerprint;
import com.shinhan.corebank.common.idempotency.IdempotencyService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class NotificationControllerTest extends IntegrationTestSupport {

    private static final String OFFSET_DATE_TIME_PATTERN = "^\\d{4}-\\d{2}-\\d{2}T.*\\+09:00$";
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 7, 10, 0);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    CustomerTestFixture customerFixture;

    @Autowired
    IdempotencyService idempotencyService;

    @Autowired
    ObjectMapper objectMapper;

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
    @DisplayName("알림이 없으면 빈 목록과 미읽음 0건을 200으로 반환한다")
    void getNotifications_noNotification_returnsEmptyItems() throws Exception {
        final Long customerId = createCustomer();

        getNotifications(customerId, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.asOf").value(matchesPattern(OFFSET_DATE_TIME_PATTERN)))
                .andExpect(jsonPath("$.data.unreadCount").value(0))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @DisplayName("발생 시각 최신순으로 주고 시각이 같으면 알림 ID가 큰 것을 먼저 준다")
    void getNotifications_sameOccurredAt_ordersByNotificationIdDescending() throws Exception {
        final Long customerId = createCustomer();
        final Long oldest = insertUnread(customerId, "TRANSFER", BASE_TIME.minusDays(1));
        final Long sameTimeFirst = insertUnread(customerId, "AUTO_TRANSFER", BASE_TIME);
        final Long sameTimeSecond = insertUnread(customerId, "PRODUCT_SUBSCRIPTION", BASE_TIME);

        getNotifications(customerId, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.items[*].notificationId",
                        contains(sameTimeSecond.intValue(), sameTimeFirst.intValue(), oldest.intValue())));
    }

    @Test
    @DisplayName("목록 항목은 표시명과 오프셋 포함 일시를 담고 미읽음이면 readAt이 null이다")
    void getNotifications_unreadItem_returnsDisplayNameAndOffsetTimes() throws Exception {
        final Long customerId = createCustomer();
        final Long notificationId = insertUnread(customerId, "SCHEDULED_TRANSFER", BASE_TIME);

        getNotifications(customerId, "")
                .andExpect(jsonPath("$.data.items[0].notificationId").value(notificationId))
                .andExpect(jsonPath("$.data.items[0].typeName").value("예약이체"))
                .andExpect(jsonPath("$.data.items[0].title").value("알림 제목"))
                .andExpect(jsonPath("$.data.items[0].content").value("알림 본문"))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andExpect(jsonPath("$.data.items[0].readAt").isEmpty())
                .andExpect(jsonPath("$.data.items[0].occurredAt").value("2026-11-07T10:00:00+09:00"));
    }

    @Test
    @DisplayName("읽은 알림 항목은 read가 true이고 readAt이 오프셋 포함 일시다")
    void getNotifications_readItem_returnsReadAtWithOffset() throws Exception {
        final Long customerId = createCustomer();
        insertRead(customerId, BASE_TIME);

        getNotifications(customerId, "")
                .andExpect(jsonPath("$.data.items[0].read").value(true))
                .andExpect(jsonPath("$.data.items[0].readAt").value("2026-11-07T09:00:00.123456+09:00"));
    }

    @Test
    @DisplayName("미읽음 건수는 현재 페이지가 아니라 고객의 전체 알림 기준이고 페이지 경계를 지킨다")
    void getNotifications_secondPage_keepsTotalUnreadCount() throws Exception {
        final Long customerId = createCustomer();
        for (int minute = 0; minute < 6; minute++) {
            insertUnread(customerId, "TRANSFER", BASE_TIME.plusMinutes(minute));
        }
        insertRead(customerId, BASE_TIME.minusDays(1));

        getNotifications(customerId, "?page=1&size=5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(6))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.totalCount").value(7))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
    }

    @Test
    @DisplayName("all=true면 size를 무시하고 전체를 주며 page 0, size 전체 건수, totalPages 1로 채운다")
    void getNotifications_allTrue_returnsEveryNotification() throws Exception {
        final Long customerId = createCustomer();
        for (int minute = 0; minute < 6; minute++) {
            insertUnread(customerId, "TRANSFER", BASE_TIME.plusMinutes(minute));
        }

        getNotifications(customerId, "?all=true&size=7&page=-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(6))
                .andExpect(jsonPath("$.data.totalCount").value(6))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(6)));
    }

    @Test
    @DisplayName("다른 고객의 알림은 목록과 미읽음 건수 어디에도 나오지 않는다")
    void getNotifications_otherCustomerNotification_isExcluded() throws Exception {
        final Long customerId = createCustomer();
        final Long otherCustomerId = createCustomer();
        final Long notificationId = insertUnread(customerId, "TRANSFER", BASE_TIME);
        insertUnread(otherCustomerId, "TRANSFER", BASE_TIME.plusMinutes(1));

        getNotifications(customerId, "")
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[*].notificationId", contains(notificationId.intValue())));
    }

    @Test
    @DisplayName("허용되지 않은 size는 CMN0005로 거부한다")
    void getNotifications_notAllowedSize_returnsCmn0005() throws Exception {
        getNotifications(createCustomer(), "?size=7")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0005"));
    }

    @Test
    @DisplayName("음수 page는 CMN0001로 거부한다")
    void getNotifications_negativePage_returnsCmn0001() throws Exception {
        getNotifications(createCustomer(), "?page=-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("인증 없이 목록을 요청하면 401 CMN0101이다")
    void getNotifications_withoutAuthentication_returnsCmn0101() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMN0101"));
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

    @Test
    @DisplayName("최초 읽음 처리는 읽음 시각을 저장하고 남은 미읽음 건수를 반환한다")
    void markAsRead_firstRequest_savesReadAtAndReturnsRemainingCount() throws Exception {
        final Long customerId = createCustomer();
        final Long notificationId = insertUnread(customerId, "TRANSFER", BASE_TIME);
        insertUnread(customerId, "TRANSFER", BASE_TIME.plusMinutes(1));

        markAsRead(customerId, notificationId, newKey())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.notificationId").value(notificationId))
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.readAt").value(matchesPattern(OFFSET_DATE_TIME_PATTERN)))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        assertThat(savedIsRead(notificationId)).isTrue();
        assertThat(savedReadAt(notificationId)).isNotNull();
    }

    @Test
    @DisplayName("이미 읽은 알림을 새 키로 다시 처리해도 200이고 최초 읽음 시각을 유지한다")
    void markAsRead_alreadyRead_keepsFirstReadAt() throws Exception {
        final Long customerId = createCustomer();
        final Long notificationId = insertRead(customerId, BASE_TIME);
        final LocalDateTime firstReadAt = savedReadAt(notificationId);

        markAsRead(customerId, notificationId, newKey())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.readAt").value("2026-11-07T09:00:00.123456+09:00"))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        assertThat(savedReadAt(notificationId)).isEqualTo(firstReadAt);
    }

    @Test
    @DisplayName("같은 키와 같은 요청은 다시 처리하지 않고 저장한 응답을 재생한다")
    void markAsRead_sameKeySameRequest_replaysStoredResponse() throws Exception {
        final Long customerId = createCustomer();
        final Long notificationId = insertUnread(customerId, "TRANSFER", BASE_TIME);
        final Long remainingId = insertUnread(customerId, "TRANSFER", BASE_TIME.plusMinutes(1));
        final String idempotencyKey = newKey();
        final String firstBody = markAsRead(customerId, notificationId, idempotencyKey)
                .andReturn()
                .getResponse()
                .getContentAsString();
        markAsRead(customerId, remainingId, newKey()).andExpect(status().isOk());

        final String replayedBody = markAsRead(customerId, notificationId, idempotencyKey)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(replayedBody).isEqualTo(firstBody);
    }

    @Test
    @DisplayName("같은 키가 처리 중이면 CMN0301로 거부한다")
    void markAsRead_sameKeyInProgress_returnsCmn0301() throws Exception {
        final Long customerId = createCustomer();
        final Long notificationId = insertUnread(customerId, "TRANSFER", BASE_TIME);
        final String idempotencyKey = newKey();
        final String fingerprint = objectMapper.writeValueAsString(
                IdempotencyFingerprint.of(customerId, null, Map.of("notificationId", notificationId)));
        idempotencyService.begin(idempotencyKey, customerId, "PATCH /notifications/{notificationId}/read", fingerprint);

        markAsRead(customerId, notificationId, idempotencyKey)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CMN0301"));
    }

    @Test
    @DisplayName("같은 키로 다른 알림을 요청하면 CMN0302로 거부한다")
    void markAsRead_sameKeyDifferentNotification_returnsCmn0302() throws Exception {
        final Long customerId = createCustomer();
        final Long firstId = insertUnread(customerId, "TRANSFER", BASE_TIME);
        final Long secondId = insertUnread(customerId, "TRANSFER", BASE_TIME.plusMinutes(1));
        final String idempotencyKey = newKey();
        markAsRead(customerId, firstId, idempotencyKey).andExpect(status().isOk());

        markAsRead(customerId, secondId, idempotencyKey)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CMN0302"));

        assertThat(savedIsRead(secondId)).isFalse();
    }

    @Test
    @DisplayName("없는 알림은 404 NTF0201이다")
    void markAsRead_missingNotification_returnsNtf0201() throws Exception {
        markAsRead(createCustomer(), Long.MAX_VALUE, newKey())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NTF0201"));
    }

    @Test
    @DisplayName("다른 고객의 알림은 403 CMN0102로 거부하고 읽음 상태를 바꾸지 않는다")
    void markAsRead_otherCustomerNotification_returnsCmn0102WithoutChange() throws Exception {
        final Long ownerId = createCustomer();
        final Long notificationId = insertUnread(ownerId, "TRANSFER", BASE_TIME);

        markAsRead(createCustomer(), notificationId, newKey())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));

        assertThat(savedIsRead(notificationId)).isFalse();
        assertThat(savedReadAt(notificationId)).isNull();
    }

    @Test
    @DisplayName("양의 정수가 아닌 알림 ID는 CMN0001로 거부한다")
    void markAsRead_notPositiveId_returnsCmn0001() throws Exception {
        final Long customerId = createCustomer();

        markAsRead(customerId, 0L, newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
        mockMvc.perform(patch("/notifications/abc/read")
                        .with(csrf())
                        .header("Idempotency-Key", newKey())
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("멱등키가 없으면 CMN0002로 거부한다")
    void markAsRead_withoutIdempotencyKey_returnsCmn0002() throws Exception {
        final Long customerId = createCustomer();
        final Long notificationId = insertUnread(customerId, "TRANSFER", BASE_TIME);

        mockMvc.perform(patch("/notifications/{notificationId}/read", notificationId)
                        .with(csrf())
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
    }

    @Test
    @DisplayName("API 문서에 알림 API 3개가 고정된 operationId로 노출된다")
    void apiDocs_exposesNotificationOperationIds() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/notifications']['get']['operationId']")
                        .value("getNotifications"))
                .andExpect(jsonPath("$['paths']['/notifications/unread-count']['get']['operationId']")
                        .value("getUnreadNotificationCount"))
                .andExpect(jsonPath("$['paths']['/notifications/{notificationId}/read']['patch']['operationId']")
                        .value("markNotificationAsRead"));
    }

    private ResultActions getNotifications(Long customerId, String queryString) throws Exception {
        return mockMvc.perform(get("/notifications" + queryString).with(authentication(authenticationOf(customerId))));
    }

    private ResultActions markAsRead(Long customerId, Long notificationId, String idempotencyKey) throws Exception {
        return mockMvc.perform(patch("/notifications/{notificationId}/read", notificationId)
                .with(csrf())
                .header("Idempotency-Key", idempotencyKey)
                .with(authentication(authenticationOf(customerId))));
    }

    private String newKey() {
        return UUID.randomUUID().toString();
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

    private boolean savedIsRead(Long notificationId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT is_read FROM notification WHERE notification_id = ?", Boolean.class, notificationId));
    }

    private LocalDateTime savedReadAt(Long notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT read_at FROM notification WHERE notification_id = ?", LocalDateTime.class, notificationId);
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        final AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
