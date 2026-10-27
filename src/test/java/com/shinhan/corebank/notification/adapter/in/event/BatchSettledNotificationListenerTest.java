package com.shinhan.corebank.notification.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.autotransfer.api.AutoTransferExecutionSettled;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.api.NotificationType;
import com.shinhan.corebank.scheduledtransfer.api.ScheduledTransferSettled;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 예약 · 자동이체 배치 이벤트가 실은 알림도 이체와 같은 리스너 하나로 들어와, 알림 참조 규약대로(예약 → 예약 건 ID,
 * 자동 → 회차 ID) 저장되는지 확인한다(#707). 문구 생성은 각 도메인의 *NotificationGeneratorTest 가 맡는다.
 */
class BatchSettledNotificationListenerTest extends IntegrationTestSupport {

    // 다른 테스트 · 시드와 겹치지 않는 #707 전용 대역
    private static final long CUSTOMER_ID = 7073L;
    private static final long SCHEDULED_TRANSFER_ID = 7_073_001L;
    private static final long AUTO_TRANSFER_EXECUTION_ID = 7_073_002L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 27, 9, 0);
    private static final Duration LISTENER_TIMEOUT = Duration.ofSeconds(10);

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
            VALUES (?, 'noti7073', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '배치알림테스터', '1990-01-01', 'noti7073@test.com', '01070737073', NOW(6), NOW(6), NOW(6))
            """,
                CUSTOMER_ID);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM notification WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update(
                "DELETE FROM EVENT_PUBLICATION WHERE EVENT_TYPE IN (?, ?) AND SERIALIZED_EVENT LIKE ?",
                ScheduledTransferSettled.class.getName(),
                AutoTransferExecutionSettled.class.getName(),
                "%\"customerId\":" + CUSTOMER_ID + ",%");
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
    }

    @Test
    @DisplayName("예약이체 확정 이벤트가 실은 알림은 예약 건 ID 를 참조로 1건 저장되고 발행 기록이 지워진다")
    void scheduledTransferSettled_savesNotificationReferencingScheduledTransfer() {
        // when
        publishInsideTransaction(ScheduledTransferSettled.builder()
                .customerId(CUSTOMER_ID)
                .refId(SCHEDULED_TRANSFER_ID)
                .status(ProcessResultStatus.SUCCESS)
                .occurredAt(OCCURRED_AT)
                .notificationRequest(
                        notification(NotificationType.SCHEDULED_TRANSFER, SCHEDULED_TRANSFER_ID, "예약이체 완료"))
                .build());

        // then
        await().atMost(LISTENER_TIMEOUT).untilAsserted(() -> {
            assertThat(notificationCount(NotificationType.SCHEDULED_TRANSFER, SCHEDULED_TRANSFER_ID))
                    .isEqualTo(1);
            assertThat(remainingPublicationCount(ScheduledTransferSettled.class))
                    .isZero();
        });
    }

    @Test
    @DisplayName("자동이체 회차가 정상 확정과 재확정으로 두 번 발행돼도 회차 ID 를 참조로 알림은 1건이다")
    void autoTransferExecutionSettledTwice_savesOneNotificationReferencingExecution() {
        // given
        AutoTransferExecutionSettled settled = AutoTransferExecutionSettled.builder()
                .customerId(CUSTOMER_ID)
                .refId(AUTO_TRANSFER_EXECUTION_ID)
                .status(ProcessResultStatus.SUCCESS)
                .occurredAt(OCCURRED_AT)
                .notificationRequest(
                        notification(NotificationType.AUTO_TRANSFER, AUTO_TRANSFER_EXECUTION_ID, "자동이체 완료"))
                .build();

        // when
        publishInsideTransaction(settled);
        publishInsideTransaction(settled);

        // then — 두 발행 기록이 모두 처리돼 지워질 때까지 기다린 뒤 센다
        await().atMost(LISTENER_TIMEOUT)
                .untilAsserted(() -> assertThat(remainingPublicationCount(AutoTransferExecutionSettled.class))
                        .isZero());
        assertThat(notificationCount(NotificationType.AUTO_TRANSFER, AUTO_TRANSFER_EXECUTION_ID))
                .isEqualTo(1);
    }

    private void publishInsideTransaction(Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> publisher.publishEvent(event));
    }

    private static NotificationRequest notification(NotificationType type, long refId, String title) {
        return NotificationRequest.builder()
                .customerId(CUSTOMER_ID)
                .type(type)
                .refId(refId)
                .title(title)
                .content("홍*동님에게 50,000원을 보냈습니다.")
                .occurredAt(OCCURRED_AT)
                .build();
    }

    private int notificationCount(NotificationType type, long refId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE type = ? AND ref_id = ?", Integer.class, type.name(), refId);
    }

    private int remainingPublicationCount(Class<?> eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM EVENT_PUBLICATION WHERE EVENT_TYPE = ? AND SERIALIZED_EVENT LIKE ?",
                Integer.class,
                eventType.getName(),
                "%\"customerId\":" + CUSTOMER_ID + ",%");
    }
}
