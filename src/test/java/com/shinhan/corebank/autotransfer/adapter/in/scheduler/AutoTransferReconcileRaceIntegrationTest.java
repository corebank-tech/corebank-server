package com.shinhan.corebank.autotransfer.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferJpaEntity;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferJpaRepository;
import com.shinhan.corebank.autotransfer.api.AutoTransferExecutionSettled;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferPersistencePort;
import com.shinhan.corebank.autotransfer.application.service.AutoTransferBatchItemProcessor;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.api.LedgerPostingContext;
import com.shinhan.corebank.transfer.api.LedgerPostingHook;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 메인 배치 락이 stale 로 판정돼 독립 재확정이 메인 배치와 겹칠 때, 같은 회차의 정상 확정과 재확정이 경합해도 회차 상태가
 * 실제 이체 결과와 같고 알림이 1건인지 실제 이체 · 실제 알림 리스너로 확인한다(#707 인수기준, PR #738 리뷰).
 * 메인 배치의 이체 트랜잭션을 기표 훅에서 멈춰 두고 그 사이에 재확정을 돌려 순서를 고정한다.
 */
@Import(AutoTransferReconcileRaceIntegrationTest.PausingHookConfig.class)
class AutoTransferReconcileRaceIntegrationTest extends IntegrationTestSupport {

    private static final String MAIN_JOB = "DAILY_TRANSFER_BATCH";
    private static final long CUSTOMER_ID = 1L;
    private static final long WITHDRAWAL_ACCOUNT_ID = 101L;
    private static final long AMOUNT = 10_000L;
    private static final LocalDate EXECUTION_DATE = LocalDate.of(2026, 10, 28);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    AutoTransferReconcileScheduler reconcileScheduler;

    @Autowired
    AutoTransferBatchItemProcessor itemProcessor;

    @Autowired
    AutoTransferPersistencePort autoTransferPersistencePort;

    @Autowired
    AutoTransferJpaRepository autoTransferJpaRepository;

    @Autowired
    PausingLedgerPostingHook pausingHook;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    Clock clock;

    private Long autoTransferId;

    @BeforeEach
    void setUp() {
        cleanUp();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TransferTestFixtures.seedCustomerAndAccounts(entityManager);
            autoTransferId = autoTransferJpaRepository
                    .save(AutoTransferJpaEntity.builder()
                            .customerId(CUSTOMER_ID)
                            .withdrawalAccountId(WITHDRAWAL_ACCOUNT_ID)
                            .depositAccountNumber("110222222222")
                            .payeeName("홍길동")
                            .amount(AMOUNT)
                            .cycleMonths(1)
                            .transferDay(28)
                            .startDate(LocalDate.of(2026, 1, 1))
                            .endDate(LocalDate.of(2027, 12, 31))
                            .nextExecutionDate(EXECUTION_DATE)
                            .myPassbookMemo("메모")
                            .recipientPassbookMemo("받는메모")
                            .status(AutoTransferStatus.NORMAL)
                            .registeredAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                            .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                            .build())
                    .getAutoTransferId();
        });
        // 메인 배치가 6시간 넘게 돌아 락이 stale 로 보이는 상태 — 독립 재확정이 메인 배치와 겹친다
        setMainBatchLock(true, LocalDateTime.now(clock).minusHours(7));
    }

    @AfterEach
    void cleanUp() {
        pausingHook.disarm();
        setMainBatchLock(false, LocalDateTime.now(clock));
        jdbcTemplate.update("DELETE FROM notification WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update(
                "DELETE FROM EVENT_PUBLICATION WHERE SERIALIZED_EVENT LIKE ?", "%\"customerId\":" + CUSTOMER_ID + ",%");
        jdbcTemplate.update("DELETE FROM audit_log WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update(
                "DELETE FROM auto_transfer_execution WHERE auto_transfer_id IN"
                        + " (SELECT auto_transfer_id FROM auto_transfer WHERE customer_id = ?)",
                CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM auto_transfer WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id = 101 AND deposit_account_id = 202");
        jdbcTemplate.update("UPDATE account SET balance = 100000, status = 'ACTIVE' WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = ?", CUSTOMER_ID);
    }

    @Test
    @DisplayName("정상 확정의 이체가 커밋되기 전에 재확정이 끼어들어도 회차는 실제 이체 결과(SUCCESS)로 확정되고 성공 알림 1건만 남는다")
    void reconcileDuringMainSettlement_settlesWithTransferResultAndOneNotification() throws Exception {
        // given — 메인 배치가 회차를 PROCESSING 으로 남기고 이체 트랜잭션 안(기표 훅)에서 멈춘다
        AutoTransfer autoTransfer =
                autoTransferPersistencePort.findById(autoTransferId).orElseThrow();
        AutoTransferExecution processing = itemProcessor.saveProcessing(autoTransfer);
        pausingHook.arm();
        CompletableFuture<Void> mainSettlement = CompletableFuture.runAsync(
                () -> itemProcessor.completeProcessing(autoTransfer, processing, EXECUTION_DATE));
        assertThat(pausingHook.awaitEntered()).isTrue();

        // when — 그 사이 독립 재확정이 돌고, 메인 배치가 이어서 끝난다
        reconcileScheduler.reconcile();
        pausingHook.release();
        mainSettlement.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        // then
        assertThat(transferStatus()).isEqualTo("SUCCESS");
        assertThat(executionStatus(processing.getExecutionId())).isEqualTo("SUCCESS");
        assertThat(nextExecutionDate()).isEqualTo(EXECUTION_DATE.plusMonths(1));
        awaitNotificationDelivery();
        assertThat(notificationContents(processing.getExecutionId())).containsExactly("홍*동님에게 10,000원을 보냈습니다.");

        // when — 경합에서 확정하지 못한 쪽(재확정)을 다시 돌린다
        reconcileScheduler.reconcile();

        // then — 알림이 늘지 않는다
        awaitNotificationDelivery();
        assertThat(notificationContents(processing.getExecutionId())).hasSize(1);
    }

    @Test
    @DisplayName("처리를 시작한 지 10분이 안 된 회차는 메인 배치가 처리 중일 수 있어 독립 재확정이 건드리지 않는다")
    void recentlyStartedExecution_isLeftToMainBatch() {
        // given — 이체 행 없이 방금 PROCESSING 으로 남은 회차
        AutoTransfer autoTransfer =
                autoTransferPersistencePort.findById(autoTransferId).orElseThrow();
        AutoTransferExecution processing = itemProcessor.saveProcessing(autoTransfer);

        // when
        reconcileScheduler.reconcile();

        // then
        assertThat(executionStatus(processing.getExecutionId())).isEqualTo("PROCESSING");
        assertThat(nextExecutionDate()).isEqualTo(EXECUTION_DATE);
        assertThat(notificationContents(processing.getExecutionId())).isEmpty();
    }

    @Test
    @DisplayName("처리를 시작한 지 10분이 지난 회차는 멈춘 것으로 보고 이체 행이 없으면 ERROR 로 확정하고 실패 알림 1건을 남긴다")
    void staleExecutionWithoutTransfer_isSettledAsErrorWithOneNotification() {
        // given — 메인 배치가 이체 전에 죽어 11분째 PROCESSING 으로 남은 회차
        AutoTransfer autoTransfer =
                autoTransferPersistencePort.findById(autoTransferId).orElseThrow();
        AutoTransferExecution processing = itemProcessor.saveProcessing(autoTransfer);
        jdbcTemplate.update(
                "UPDATE auto_transfer_execution SET executed_at = ? WHERE execution_id = ?",
                LocalDateTime.now(clock).minusMinutes(11),
                processing.getExecutionId());

        // when
        reconcileScheduler.reconcile();

        // then
        assertThat(executionStatus(processing.getExecutionId())).isEqualTo("ERROR");
        assertThat(nextExecutionDate()).isEqualTo(EXECUTION_DATE.plusMonths(1));
        awaitNotificationDelivery();
        assertThat(notificationContents(processing.getExecutionId())).containsExactly("홍*동님에게 10,000원을 보내지 못했습니다.");
    }

    private void awaitNotificationDelivery() {
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM EVENT_PUBLICATION WHERE EVENT_TYPE = ? AND SERIALIZED_EVENT LIKE ?",
                        Integer.class,
                        AutoTransferExecutionSettled.class.getName(),
                        "%\"customerId\":" + CUSTOMER_ID + ",%"))
                .isZero());
    }

    private String transferStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM transfer WHERE source_id = ? AND withdrawal_account_id = 101",
                String.class,
                autoTransferId);
    }

    private String executionStatus(Long executionId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM auto_transfer_execution WHERE execution_id = ?", String.class, executionId);
    }

    private LocalDate nextExecutionDate() {
        return jdbcTemplate.queryForObject(
                "SELECT next_execution_date FROM auto_transfer WHERE auto_transfer_id = ?",
                LocalDate.class,
                autoTransferId);
    }

    private List<String> notificationContents(Long executionId) {
        return jdbcTemplate.queryForList(
                "SELECT content FROM notification WHERE type = 'AUTO_TRANSFER' AND ref_id = ?",
                String.class,
                executionId);
    }

    private void setMainBatchLock(boolean running, LocalDateTime updatedAt) {
        jdbcTemplate.update(
                "UPDATE batch_execution_lock SET currently_running = ?, updated_at = ? WHERE job_name = ?",
                running,
                updatedAt,
                MAIN_JOB);
    }

    // 이체 트랜잭션 안(기표 직후, 커밋 전)에서 멈춰 재확정이 끼어들 틈을 만든다. 무장했을 때 한 번만 멈춘다
    static class PausingLedgerPostingHook implements LedgerPostingHook {

        private volatile CountDownLatch entered = new CountDownLatch(1);
        private volatile CountDownLatch released = new CountDownLatch(1);
        private volatile boolean armed;

        void arm() {
            entered = new CountDownLatch(1);
            released = new CountDownLatch(1);
            armed = true;
        }

        void disarm() {
            armed = false;
            released.countDown();
        }

        boolean awaitEntered() throws InterruptedException {
            return entered.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }

        void release() {
            released.countDown();
        }

        @Override
        public void afterLedger(LedgerPostingContext context) {
            if (!armed) {
                return;
            }
            armed = false;
            entered.countDown();
            try {
                released.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @TestConfiguration
    static class PausingHookConfig {

        @Bean
        PausingLedgerPostingHook pausingLedgerPostingHook() {
            return new PausingLedgerPostingHook();
        }
    }
}
