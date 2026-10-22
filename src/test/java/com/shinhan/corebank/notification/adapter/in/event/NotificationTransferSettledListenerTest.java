package com.shinhan.corebank.notification.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.notification.adapter.out.persistence.NotificationJpaEntity;
import com.shinhan.corebank.notification.adapter.out.persistence.NotificationJpaRepository;
import com.shinhan.corebank.notification.domain.NotificationType;
import com.shinhan.corebank.transfer.api.TransferSettled;
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
 * 이체가 확정되면(TransferSettled) 커밋 뒤 비동기 구독자가 알림을 1건 남기고 발행 기록을 지우는지 확인한다(#706).
 * 엔진을 거치지 않고 이벤트를 트랜잭션 안에서 직접 발행한다 — 엔진의 발행은 #505 테스트가 맡는다.
 */
class NotificationTransferSettledListenerTest extends IntegrationTestSupport {

    // 다른 테스트 · 시드와 겹치지 않는 #706 전용 대역
    private static final long CUSTOMER_ID = 7061L;
    private static final long TRANSFER_ID = 7_061_001L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 22, 9, 30, 15, 123_456_000);
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
            VALUES (?, 'noti7061', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '알림테스터', '1990-01-01', 'noti7061@test.com', '01070617061', NOW(6), NOW(6), NOW(6))
            """,
                CUSTOMER_ID);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM notification WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update(
                "DELETE FROM EVENT_PUBLICATION WHERE EVENT_TYPE = ? AND SERIALIZED_EVENT LIKE ?",
                TransferSettled.class.getName(),
                refIdPattern());
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
    }

    @Test
    @DisplayName("즉시이체가 확정되면 커밋 뒤 알림이 미읽음으로 1건 생기고 발행 기록은 지워진다")
    void immediateTransferSettled_createsOneUnreadNotificationAndClearsPublication() {
        // when
        publishInsideTransaction(settledImmediateTransfer());

        // then
        await().atMost(LISTENER_TIMEOUT).untilAsserted(() -> {
            assertThat(notificationCount()).isEqualTo(1);
            assertThat(remainingPublicationCount()).isZero();
        });
        NotificationJpaEntity notification = savedNotification();
        assertThat(notification.getType()).isEqualTo(NotificationType.TRANSFER);
        assertThat(notification.getRefId()).isEqualTo(TRANSFER_ID);
        assertThat(notification.getTitle()).isEqualTo("이체 완료");
        assertThat(notification.getContent()).isEqualTo("홍*동님에게 1,000,000원을 보냈습니다.");
        assertThat(notification.getIsRead()).isFalse();
        assertThat(notification.getReadAt()).isNull();
        assertThat(notification.getOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("같은 이체 결과를 두 번 받아도 알림은 1건이다 — 재제출은 최소 한 번 전달이다")
    void sameTransferSettledTwice_keepsOneNotification() {
        // when
        publishInsideTransaction(settledImmediateTransfer());
        publishInsideTransaction(settledImmediateTransfer());

        // then — 두 발행 기록이 모두 처리돼 지워질 때까지 기다린 뒤 센다
        await().atMost(LISTENER_TIMEOUT)
                .untilAsserted(() -> assertThat(remainingPublicationCount()).isZero());
        assertThat(notificationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("실패한 이체는 실패 알림으로 남고, 고객이 조치할 수 있는 사유를 본문에 붙인다")
    void failedImmediateTransfer_createsFailureNotificationWithReason() {
        // when
        publishInsideTransaction(TransferSettled.builder()
                .customerId(CUSTOMER_ID)
                .refId(TRANSFER_ID)
                .txType("IMMEDIATE_TRANSFER")
                .status(ProcessResultStatus.ERROR)
                .errorCode("TRF0303")
                .occurredAt(OCCURRED_AT)
                .amount(1_000_000L)
                .counterpartyName("홍*동")
                .build());

        // then
        await().atMost(LISTENER_TIMEOUT)
                .untilAsserted(() -> assertThat(notificationCount()).isEqualTo(1));
        NotificationJpaEntity notification = savedNotification();
        assertThat(notification.getTitle()).isEqualTo("이체 실패");
        assertThat(notification.getContent()).isEqualTo("홍*동님에게 1,000,000원을 보내지 못했습니다. 출금계좌 잔액이 부족합니다.");
    }

    private void publishInsideTransaction(TransferSettled event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> publisher.publishEvent(event));
    }

    private static TransferSettled settledImmediateTransfer() {
        return TransferSettled.builder()
                .customerId(CUSTOMER_ID)
                .refId(TRANSFER_ID)
                .txType("IMMEDIATE_TRANSFER")
                .status(ProcessResultStatus.SUCCESS)
                .occurredAt(OCCURRED_AT)
                .amount(1_000_000L)
                .counterpartyName("홍*동")
                .build();
    }

    // 시각은 앱과 같은 경로(Hibernate, jdbc.time_zone)로 읽는다 — JDBC 로 날값을 읽으면 JVM 시간대만큼 밀려 보인다
    private NotificationJpaEntity savedNotification() {
        return notificationRepository.findAll().stream()
                .filter(saved -> saved.getCustomerId().equals(CUSTOMER_ID))
                .findFirst()
                .orElseThrow();
    }

    private int notificationCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE type = 'TRANSFER' AND ref_id = ?", Integer.class, TRANSFER_ID);
    }

    private int remainingPublicationCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM EVENT_PUBLICATION WHERE EVENT_TYPE = ? AND SERIALIZED_EVENT LIKE ?",
                Integer.class,
                TransferSettled.class.getName(),
                refIdPattern());
    }

    private static String refIdPattern() {
        return "%\"refId\":" + TRANSFER_ID + "%";
    }
}
