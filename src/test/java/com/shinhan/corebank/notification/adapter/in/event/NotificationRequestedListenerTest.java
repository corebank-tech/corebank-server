package com.shinhan.corebank.notification.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.notification.adapter.out.persistence.NotificationJpaEntity;
import com.shinhan.corebank.notification.adapter.out.persistence.NotificationJpaRepository;
import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.api.NotificationType;
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
 * 업무 이벤트가 알림 계약(NotificationRequestCarrier)으로 실어 보낸 알림을, 커밋 뒤 리스너 하나가 받아 저장하는지 확인한다
 * (#706, PR #723 리뷰). 실제 업무 이벤트인 TransferSettled 를 트랜잭션 안에서 직접 발행한다 — 문구는 발행 쪽이
 * 만든 그대로 저장되는지 보고, 문구 생성은 TransferNotificationComposerTest 가 맡는다.
 */
class NotificationRequestedListenerTest extends IntegrationTestSupport {

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
    @DisplayName("알림을 실은 업무 이벤트가 커밋되면 리스너 하나가 받아 미읽음 알림 1건을 남기고 발행 기록을 지운다")
    void carrierEventCommitted_savesOneUnreadNotificationAndClearsPublication() {
        // when
        publishInsideTransaction(settledWith(notification(CUSTOMER_ID, "이체 완료", "홍*동님에게 1,000,000원을 보냈습니다.")));

        // then
        await().atMost(LISTENER_TIMEOUT).untilAsserted(() -> {
            assertThat(notificationCount()).isEqualTo(1);
            assertThat(remainingPublicationCount()).isZero();
        });
        // 시각은 앱과 같은 경로(Hibernate, jdbc.time_zone)로 읽는다 — JDBC 로 날값을 읽으면 JVM 시간대만큼 밀려 보인다
        NotificationJpaEntity saved = savedNotification();
        assertThat(saved.getType()).isEqualTo(NotificationType.TRANSFER);
        assertThat(saved.getRefId()).isEqualTo(TRANSFER_ID);
        assertThat(saved.getTitle()).isEqualTo("이체 완료");
        assertThat(saved.getContent()).isEqualTo("홍*동님에게 1,000,000원을 보냈습니다.");
        assertThat(saved.getIsRead()).isFalse();
        assertThat(saved.getReadAt()).isNull();
        assertThat(saved.getOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("같은 알림을 두 번 받아도 1건이다 — 재제출은 최소 한 번 전달이다")
    void sameNotificationTwice_keepsOneNotification() {
        // when
        TransferSettled settled = settledWith(notification(CUSTOMER_ID, "이체 완료", "1,000,000원을 보냈습니다."));
        publishInsideTransaction(settled);
        publishInsideTransaction(settled);

        // then — 두 발행 기록이 모두 처리돼 지워질 때까지 기다린 뒤 센다
        await().atMost(LISTENER_TIMEOUT)
                .untilAsserted(() -> assertThat(remainingPublicationCount()).isZero());
        assertThat(notificationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("발행 쪽이 만든 실패 문구를 해석하지 않고 그대로 저장한다")
    void failureNotification_isSavedAsComposedByPublisher() {
        // when
        publishInsideTransaction(
                settledWith(notification(CUSTOMER_ID, "이체 실패", "홍*동님에게 1,000,000원을 보내지 못했습니다. 출금계좌 잔액이 부족합니다.")));

        // then
        await().atMost(LISTENER_TIMEOUT)
                .untilAsserted(() -> assertThat(notificationCount()).isEqualTo(1));
        NotificationJpaEntity saved = savedNotification();
        assertThat(saved.getTitle()).isEqualTo("이체 실패");
        assertThat(saved.getContent()).isEqualTo("홍*동님에게 1,000,000원을 보내지 못했습니다. 출금계좌 잔액이 부족합니다.");
    }

    @Test
    @DisplayName("알림을 싣지 않은 업무 이벤트는 알림을 만들지 않는다 — 예약 · 자동이체의 이체 확정(#707)")
    void eventWithoutNotification_createsNoNotification() {
        // when
        publishInsideTransaction(settledWith(null));

        // then
        await().atMost(LISTENER_TIMEOUT)
                .untilAsserted(() -> assertThat(remainingPublicationCount()).isZero());
        assertThat(notificationCount()).isZero();
    }

    @Test
    @DisplayName("수신 고객이 없는 알림은 저장하지 않고, 다시 보낼 발행 기록도 남기지 않는다")
    void notificationWithoutCustomer_createsNoNotificationAndLeavesNoPublication() {
        // when
        publishInsideTransaction(settledWith(notification(null, "이체 실패", "1,000,000원을 보내지 못했습니다.")));

        // then
        await().atMost(LISTENER_TIMEOUT)
                .untilAsserted(() -> assertThat(remainingPublicationCount()).isZero());
        assertThat(notificationCount()).isZero();
    }

    private void publishInsideTransaction(TransferSettled event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> publisher.publishEvent(event));
    }

    private static TransferSettled settledWith(NotificationRequest notification) {
        return TransferSettled.builder()
                .customerId(notification == null ? Long.valueOf(CUSTOMER_ID) : notification.customerId())
                .refId(TRANSFER_ID)
                .txType("IMMEDIATE_TRANSFER")
                .status(ProcessResultStatus.SUCCESS)
                .occurredAt(OCCURRED_AT)
                .notificationRequest(notification)
                .build();
    }

    private static NotificationRequest notification(Long customerId, String title, String content) {
        return NotificationRequest.builder()
                .customerId(customerId)
                .type(NotificationType.TRANSFER)
                .refId(TRANSFER_ID)
                .title(title)
                .content(content)
                .occurredAt(OCCURRED_AT)
                .build();
    }

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
