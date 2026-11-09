package com.shinhan.corebank.notification.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.autotransfer.api.AutoTransferExecutionSettled;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.api.NotificationType;
import com.shinhan.corebank.notification.application.port.out.NotificationCommandPort;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 리스너가 순간 실패는 그 자리에서 다시 시도해 발행 기록을 남기지 않고, 몇 번 해도 같은 계약 위반은 다시 시도하지
 * 않고 기록을 남기는지 확인한다(#709). 시도 횟수는 Spring 이 실패한 시도마다 내는 MethodRetryEvent 로 센다.
 */
@Import(NotificationRetryEventRecorder.class)
class NotificationRetryTest extends IntegrationTestSupport {

    // 다른 테스트 · 시드와 겹치지 않는 #709 전용 대역
    private static final long CUSTOMER_ID = 7091L;
    private static final long EXECUTION_ID = 7_091_001L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 31, 9, 0);
    private static final Duration LISTENER_TIMEOUT = Duration.ofSeconds(10);

    @MockitoSpyBean
    NotificationCommandPort notificationCommandPort;

    @Autowired
    NotificationRetryEventRecorder retryEventRecorder;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update(
                """
            INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at)
            VALUES (?, 'noti7091', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '재시도테스터', '1990-01-01', 'noti7091@test.com', '01070917091', NOW(6), NOW(6), NOW(6))
            """,
                CUSTOMER_ID);
    }

    @AfterEach
    void cleanUp() {
        retryEventRecorder.clear();
        jdbcTemplate.update("DELETE FROM notification WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update(
                "DELETE FROM EVENT_PUBLICATION WHERE SERIALIZED_EVENT LIKE ?", "%\"customerId\":" + CUSTOMER_ID + ",%");
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
    }

    @Test
    @DisplayName("알림 저장이 한 번 순간 실패해도 그 자리에서 다시 시도해 알림 1건을 남기고 발행 기록은 지워진다")
    void transientFailureOnce_retriesImmediatelyAndCompletes() {
        // given — 첫 저장만 DB 순간 장애로 실패한다
        doThrow(new DataAccessResourceFailureException("순간 장애"))
                .doCallRealMethod()
                .when(notificationCommandPort)
                .saveIfAbsent(any());

        // when
        publishInsideTransaction(settled("자동이체 완료"));

        // then
        await().atMost(LISTENER_TIMEOUT)
                .untilAsserted(() -> assertThat(publicationCount()).isZero());
        assertThat(notificationCount()).isEqualTo(1);
        verify(notificationCommandPort, times(2)).saveIfAbsent(any());
    }

    @Test
    @DisplayName("알림 계약을 어기면(빈 제목) 다시 시도하지 않고 한 번만 실패한 채 발행 기록을 FAILED 로 남긴다")
    void contractViolation_failsOnceWithoutRetryAndLeavesFailedPublication() {
        // given — 빈 제목은 알림 계약 위반이다
        AutoTransferExecutionSettled blankTitle = settled(" ");

        // when
        publishInsideTransaction(blankTitle);

        // then — 포기 이벤트까지 기다려야 재시도가 더 오지 않는다는 것을 볼 수 있다
        await().atMost(LISTENER_TIMEOUT).until(() -> retryEventRecorder.hasExhaustedRetries(EXECUTION_ID));
        assertThat(retryEventRecorder.failedAttempts()).isEqualTo(1);
        assertThat(publicationStatuses()).containsExactly("FAILED");
        assertThat(notificationCount()).isZero();
    }

    private void publishInsideTransaction(Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> publisher.publishEvent(event));
    }

    private static AutoTransferExecutionSettled settled(String title) {
        return AutoTransferExecutionSettled.builder()
                .customerId(CUSTOMER_ID)
                .refId(EXECUTION_ID)
                .status(ProcessResultStatus.SUCCESS)
                .occurredAt(OCCURRED_AT)
                .notificationRequest(NotificationRequest.builder()
                        .customerId(CUSTOMER_ID)
                        .type(NotificationType.AUTO_TRANSFER)
                        .refId(EXECUTION_ID)
                        .title(title)
                        .content("홍*동님에게 50,000원을 보냈습니다.")
                        .occurredAt(OCCURRED_AT)
                        .build())
                .build();
    }

    private int notificationCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE type = 'AUTO_TRANSFER' AND ref_id = ?",
                Integer.class,
                EXECUTION_ID);
    }

    private int publicationCount() {
        return publicationStatuses().size();
    }

    private List<String> publicationStatuses() {
        return jdbcTemplate.queryForList(
                "SELECT STATUS FROM EVENT_PUBLICATION WHERE SERIALIZED_EVENT LIKE ?",
                String.class,
                "%\"customerId\":" + CUSTOMER_ID + ",%");
    }
}
