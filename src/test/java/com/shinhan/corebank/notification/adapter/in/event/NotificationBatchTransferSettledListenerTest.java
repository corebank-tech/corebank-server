package com.shinhan.corebank.notification.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.autotransfer.api.AutoTransferExecutionSettled;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.notification.adapter.out.persistence.NotificationJpaEntity;
import com.shinhan.corebank.notification.adapter.out.persistence.NotificationJpaRepository;
import com.shinhan.corebank.notification.domain.NotificationType;
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
 * {@link NotificationScheduledTransferSettledListener} · {@link NotificationAutoTransferExecutionSettledListener} —
 * 예약이체 · 자동이체 회차가 확정되면 배치가 발행한 이벤트로 알림이 1건 생기는지 확인한다(#707). 배치를 거치지 않고
 * 이벤트를 트랜잭션 안에서 직접 발행한다 — 배치의 발행은 각 배치 테스트가 맡는다.
 */
class NotificationBatchTransferSettledListenerTest extends IntegrationTestSupport {

    // 다른 테스트 · 시드와 겹치지 않는 #707 전용 대역
    private static final long CUSTOMER_ID = 7071L;
    private static final long SCHEDULED_TRANSFER_ID = 7_071_001L;
    private static final long EXECUTION_ID = 7_071_002L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 23, 0, 30, 5, 123_456_000);
    private static final Duration LISTENER_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    NotificationJpaRepository notificationRepository;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update(
                """
            INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at)
            VALUES (?, 'noti7071', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '알림테스터', '1990-01-01', 'noti7071@test.com', '01070717071', NOW(6), NOW(6), NOW(6))
            """,
                CUSTOMER_ID);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM notification WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update(
                "DELETE FROM EVENT_PUBLICATION WHERE (EVENT_TYPE = ? AND SERIALIZED_EVENT LIKE ?)"
                        + " OR (EVENT_TYPE = ? AND SERIALIZED_EVENT LIKE ?)",
                ScheduledTransferSettled.class.getName(),
                refIdPattern(SCHEDULED_TRANSFER_ID),
                AutoTransferExecutionSettled.class.getName(),
                refIdPattern(EXECUTION_ID));
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
    }

    @Test
    @DisplayName("예약이체가 확정되면 예약 건 ID 로 예약이체 알림이 1건 생기고 발행 기록은 지워진다")
    void scheduledTransferSettled_createsScheduledTransferNotification() {
        // when
        publishInsideTransaction(ScheduledTransferSettled.builder()
                .customerId(CUSTOMER_ID)
                .refId(SCHEDULED_TRANSFER_ID)
                .status(ProcessResultStatus.SUCCESS)
                .occurredAt(OCCURRED_AT)
                .amount(10_000L)
                .counterpartyName("홍*동")
                .build());

        // then
        await().atMost(LISTENER_TIMEOUT).untilAsserted(() -> {
            assertThat(notificationCount(NotificationType.SCHEDULED_TRANSFER, SCHEDULED_TRANSFER_ID))
                    .isEqualTo(1);
            assertThat(remainingPublicationCount()).isZero();
        });
        NotificationJpaEntity notification = savedNotification();
        assertThat(notification.getTitle()).isEqualTo("예약이체 완료");
        assertThat(notification.getContent()).isEqualTo("홍*동님에게 10,000원을 보냈습니다.");
        assertThat(notification.getOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("자동이체 회차가 실패로 확정되면 회차 ID 로 자동이체 실패 알림이 1건 생긴다")
    void autoTransferExecutionSettled_createsAutoTransferNotification() {
        // when
        publishInsideTransaction(AutoTransferExecutionSettled.builder()
                .customerId(CUSTOMER_ID)
                .refId(EXECUTION_ID)
                .status(ProcessResultStatus.ERROR)
                .errorCode("TRF0303")
                .occurredAt(OCCURRED_AT)
                .amount(10_000L)
                .counterpartyName("홍*동")
                .build());

        // then
        await().atMost(LISTENER_TIMEOUT).untilAsserted(() -> {
            assertThat(notificationCount(NotificationType.AUTO_TRANSFER, EXECUTION_ID))
                    .isEqualTo(1);
            assertThat(remainingPublicationCount()).isZero();
        });
        NotificationJpaEntity notification = savedNotification();
        assertThat(notification.getTitle()).isEqualTo("자동이체 실패");
        assertThat(notification.getContent()).isEqualTo("홍*동님에게 10,000원을 보내지 못했습니다. 출금계좌 잔액이 부족합니다.");
    }

    private void publishInsideTransaction(Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> publisher.publishEvent(event));
    }

    // 시각은 앱과 같은 경로(Hibernate, jdbc.time_zone)로 읽는다 — JDBC 로 날값을 읽으면 JVM 시간대만큼 밀려 보인다
    private NotificationJpaEntity savedNotification() {
        return notificationRepository.findAll().stream()
                .filter(saved -> saved.getCustomerId().equals(CUSTOMER_ID))
                .findFirst()
                .orElseThrow();
    }

    private int notificationCount(NotificationType type, long refId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE type = ? AND ref_id = ?", Integer.class, type.name(), refId);
    }

    private int remainingPublicationCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM EVENT_PUBLICATION WHERE (EVENT_TYPE = ? AND SERIALIZED_EVENT LIKE ?)"
                        + " OR (EVENT_TYPE = ? AND SERIALIZED_EVENT LIKE ?)",
                Integer.class,
                ScheduledTransferSettled.class.getName(),
                refIdPattern(SCHEDULED_TRANSFER_ID),
                AutoTransferExecutionSettled.class.getName(),
                refIdPattern(EXECUTION_ID));
    }

    // 전용 대역 refId 는 7자리라 다른 테스트의 발행 기록과 겹치지 않는다
    private static String refIdPattern(long refId) {
        return "%\"refId\":" + refId + "%";
    }
}
