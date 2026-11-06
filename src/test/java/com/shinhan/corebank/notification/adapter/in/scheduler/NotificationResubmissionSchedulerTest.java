package com.shinhan.corebank.notification.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.autotransfer.api.AutoTransferExecutionSettled;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.notification.adapter.in.event.NotificationRetryEventRecorder;
import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.api.NotificationType;
import com.shinhan.corebank.notification.application.port.out.NotificationCommandPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 즉시 재시도까지 실패하거나 처리 중에 멈춘 알림 발행 기록을 주기 재제출이 다시 보내고, 상한을 넘긴 기록은 남겨 둔 채
 * 경보로 알리는지 확인한다(#709). 재제출은 스케줄을 기다리지 않고 직접 부른다.
 */
@ExtendWith(OutputCaptureExtension.class)
@Import(NotificationRetryEventRecorder.class)
class NotificationResubmissionSchedulerTest extends IntegrationTestSupport {

    // 다른 테스트 · 시드와 겹치지 않는 #709 전용 대역
    private static final long CUSTOMER_ID = 7092L;
    // 컨텍스트를 같이 쓰는 테스트끼리 같은 이벤트를 다시 발행하면 처리 중 목록에서 겹친다 — 발행마다 회차를 새로 받는다
    private static final AtomicLong EXECUTION_IDS = new AtomicLong(7_092_000L);
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 2, 9, 0);
    private static final Duration LISTENER_TIMEOUT = Duration.ofSeconds(10);
    private static final String ABANDONED_GAUGE = "notification.publication.abandoned";
    // 라이브러리가 한 번에 읽는 실패 기록 수
    private static final int RESUBMISSION_BATCH_SIZE =
            ResubmissionOptions.defaults().getBatchSize();
    // 시도 횟수는 첫 전달 1번에 재제출마다 1씩 더한다. 재제출 상한 3번을 다 쓴 기록은 4다
    private static final int ATTEMPTS_AFTER_FIRST_DELIVERY = 1;
    private static final int ATTEMPTS_AT_RESUBMISSION_LIMIT = 4;

    @MockitoSpyBean
    NotificationCommandPort notificationCommandPort;

    @Autowired
    NotificationRetryEventRecorder retryEventRecorder;

    @Autowired
    NotificationResubmissionScheduler notificationResubmissionScheduler;

    @Autowired
    MeterRegistry meterRegistry;

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
            VALUES (?, 'noti7092', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '재제출테스터', '1990-01-01', 'noti7092@test.com', '01070927092', NOW(6), NOW(6), NOW(6))
            """,
                CUSTOMER_ID);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM notification WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update(
                "DELETE FROM EVENT_PUBLICATION WHERE SERIALIZED_EVENT LIKE ?", "%\"customerId\":" + CUSTOMER_ID + ",%");
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
    }

    @Test
    @DisplayName("즉시 재시도까지 실패해 FAILED 로 남은 기록은 저장이 회복된 뒤 재제출 한 번으로 알림 1건을 남기고 지워진다")
    void failedPublication_resubmittedAfterRecovery_savesNotificationOnce() {
        // given
        long executionId = leaveFailedPublication();
        recoverStorage();

        // when
        notificationResubmissionScheduler.resubmitFailed();

        // then
        await().atMost(LISTENER_TIMEOUT)
                .untilAsserted(() -> assertThat(publicationRow(executionId)).isNull());
        assertThat(notificationCount(executionId)).isEqualTo(1);
    }

    @Test
    @DisplayName("재제출을 세 번 한 기록은 다시 보내지 않고 남겨 두며 ERROR 로그와 포기 지표로 알린다")
    void publicationAtAttemptLimit_isNotResubmittedAndRaisesAlarm(CapturedOutput output) {
        // given
        long executionId = leaveFailedPublication();
        recoverStorage();
        jdbcTemplate.update(
                "UPDATE EVENT_PUBLICATION SET COMPLETION_ATTEMPTS = ? WHERE SERIALIZED_EVENT LIKE ?",
                ATTEMPTS_AT_RESUBMISSION_LIMIT,
                refIdPattern(executionId));

        // when
        notificationResubmissionScheduler.resubmitFailed();

        // then — 가져가면 재제출 호출 안에서 시도 횟수가 오르므로 바로 본다
        Map<String, Object> row = publicationRow(executionId);
        assertThat(row.get("STATUS")).isEqualTo("FAILED");
        assertThat(row.get("COMPLETION_ATTEMPTS")).isEqualTo(ATTEMPTS_AT_RESUBMISSION_LIMIT);
        assertThat(meterRegistry.get(ABANDONED_GAUGE).gauge().value()).isEqualTo(1.0);
        assertThat(output).contains("재제출 상한").contains(String.valueOf(row.get("ID")));
    }

    @Test
    @DisplayName("처리 중에 10분 넘게 멈춘 기록은 FAILED 로 바뀌어 재제출되고, 10분이 안 된 기록은 처리 중 그대로 둔다")
    void stuckProcessingPublication_markedFailedAfterTenMinutesAndResubmitted() {
        // given — 처리 도중 서버가 죽은 상황을 흉내 낸다
        long stuckId = leaveFailedPublication();
        long recentId = leaveFailedPublication();
        recoverStorage();
        markProcessingSince(stuckId, Instant.now().minus(Duration.ofMinutes(11)));
        markProcessingSince(recentId, Instant.now().minus(Duration.ofMinutes(5)));

        // when — 내장 모니터(테스트에서는 1초마다)가 멈춘 기록을 FAILED 로 바꾼 뒤 재제출한다
        await().atMost(LISTENER_TIMEOUT)
                .untilAsserted(
                        () -> assertThat(publicationRow(stuckId).get("STATUS")).isEqualTo("FAILED"));
        notificationResubmissionScheduler.resubmitFailed();

        // then
        await().atMost(LISTENER_TIMEOUT)
                .untilAsserted(() -> assertThat(publicationRow(stuckId)).isNull());
        assertThat(notificationCount(stuckId)).isEqualTo(1);
        assertThat(publicationRow(recentId).get("STATUS")).isEqualTo("PROCESSING");
        assertThat(notificationCount(recentId)).isZero();
    }

    @Test
    @DisplayName("한 인스턴스가 다시 보내는 중인 기록은 다른 인스턴스가 재제출해도 가져가지 않고, 알림은 1건이다")
    void resubmissionInProgress_isNotClaimedByAnotherInstance() throws Exception {
        // given — 첫 재제출 실행이 저장 직전에 멈추게 해 두 재제출이 실제로 겹치게 한다
        long executionId = leaveFailedPublication();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
                    entered.countDown();
                    release.await(LISTENER_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                    return invocation.callRealMethod();
                })
                .when(notificationCommandPort)
                .saveIfAbsent(any());
        notificationResubmissionScheduler.resubmitFailed();
        assertThat(entered.await(LISTENER_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .isTrue();

        // when — 다른 인스턴스의 재제출
        notificationResubmissionScheduler.resubmitFailed();
        Object attemptsWhileInProgress = publicationRow(executionId).get("COMPLETION_ATTEMPTS");
        release.countDown();

        // then
        assertThat(attemptsWhileInProgress).isEqualTo(ATTEMPTS_AFTER_FIRST_DELIVERY + 1);
        await().atMost(LISTENER_TIMEOUT)
                .untilAsserted(() -> assertThat(publicationRow(executionId)).isNull());
        assertThat(notificationCount(executionId)).isEqualTo(1);
    }

    @Test
    @DisplayName("포기한 기록이 읽기 묶음을 가득 채우면 그 뒤의 새 실패 기록은 밀리고, 경보가 묶음이 찼다고 알린다")
    void abandonedPublicationsFillingBatch_holdBackNewerOneAndRaiseAlarm(CapturedOutput output) {
        // given — 하루 전에 포기한 기록 100건이 오래된 순 읽기의 앞자리를 차지한다
        long executionId = leaveFailedPublication();
        recoverStorage();
        copyAsAbandoned(executionId, RESUBMISSION_BATCH_SIZE, Instant.now().minus(Duration.ofDays(1)));

        // when
        notificationResubmissionScheduler.resubmitFailed();

        // then
        Map<String, Object> newer = publicationRow(executionId);
        assertThat(newer.get("STATUS")).isEqualTo("FAILED");
        assertThat(newer.get("COMPLETION_ATTEMPTS")).isEqualTo(ATTEMPTS_AFTER_FIRST_DELIVERY);
        assertThat(meterRegistry.get(ABANDONED_GAUGE).gauge().value()).isEqualTo((double) RESUBMISSION_BATCH_SIZE);
        assertThat(output).contains("그 뒤 실패 기록이 재제출되지 않는다");
    }

    // 알림 저장을 DB 장애로 계속 실패시켜 즉시 재시도까지 소진한 FAILED 기록을 만든다. 포기 이벤트까지 기다려야 마지막
    // 시도가 기록을 다시 건드리지 않는다
    private long leaveFailedPublication() {
        long executionId = EXECUTION_IDS.incrementAndGet();
        doThrow(new DataAccessResourceFailureException("저장 장애"))
                .when(notificationCommandPort)
                .saveIfAbsent(any());

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> publisher.publishEvent(settled(executionId)));

        await().atMost(LISTENER_TIMEOUT).until(() -> retryEventRecorder.hasExhaustedRetries(executionId));
        assertThat(publicationRow(executionId).get("STATUS")).isEqualTo("FAILED");
        return executionId;
    }

    private void recoverStorage() {
        doCallRealMethod().when(notificationCommandPort).saveIfAbsent(any());
    }

    // 라이브러리는 기록을 만들 때 마지막 재제출 시각을 발행 시각과 같게 넣고, 멈춤은 마지막 재제출 시각으로 판정한다
    private void markProcessingSince(long executionId, Instant publishedAt) {
        jdbcTemplate.update(
                """
            UPDATE EVENT_PUBLICATION SET STATUS = 'PROCESSING', PUBLICATION_DATE = ?, LAST_RESUBMISSION_DATE = ?
            WHERE SERIALIZED_EVENT LIKE ?
            """,
                Timestamp.from(publishedAt),
                Timestamp.from(publishedAt),
                refIdPattern(executionId));
    }

    private void copyAsAbandoned(long executionId, int count, Instant publishedAt) {
        Map<String, Object> source = publicationRow(executionId);
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    """
                INSERT INTO EVENT_PUBLICATION (ID, LISTENER_ID, EVENT_TYPE, SERIALIZED_EVENT, PUBLICATION_DATE, STATUS, COMPLETION_ATTEMPTS)
                VALUES (?, ?, ?, ?, ?, 'FAILED', ?)
                """,
                    UUID.randomUUID().toString(),
                    source.get("LISTENER_ID"),
                    source.get("EVENT_TYPE"),
                    source.get("SERIALIZED_EVENT"),
                    Timestamp.from(publishedAt),
                    ATTEMPTS_AT_RESUBMISSION_LIMIT);
        }
    }

    private static AutoTransferExecutionSettled settled(long executionId) {
        return AutoTransferExecutionSettled.builder()
                .customerId(CUSTOMER_ID)
                .refId(executionId)
                .status(ProcessResultStatus.SUCCESS)
                .occurredAt(OCCURRED_AT)
                .notificationRequest(NotificationRequest.builder()
                        .customerId(CUSTOMER_ID)
                        .type(NotificationType.AUTO_TRANSFER)
                        .refId(executionId)
                        .title("자동이체 완료")
                        .content("홍*동님에게 50,000원을 보냈습니다.")
                        .occurredAt(OCCURRED_AT)
                        .build())
                .build();
    }

    private static String refIdPattern(long executionId) {
        return "%\"refId\":" + executionId + "%";
    }

    // 가장 최근에 발행한 기록 한 건. 묶음 테스트의 복사본(하루 전)보다 늘 뒤에 있다
    private Map<String, Object> publicationRow(long executionId) {
        return jdbcTemplate
                .queryForList(
                        "SELECT ID, STATUS, COMPLETION_ATTEMPTS, LISTENER_ID, EVENT_TYPE, SERIALIZED_EVENT"
                                + " FROM EVENT_PUBLICATION WHERE SERIALIZED_EVENT LIKE ? ORDER BY PUBLICATION_DATE DESC LIMIT 1",
                        refIdPattern(executionId))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private int notificationCount(long executionId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE type = 'AUTO_TRANSFER' AND ref_id = ?",
                Integer.class,
                executionId);
    }
}
