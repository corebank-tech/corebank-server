package com.shinhan.corebank.scheduledtransfer.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.audit.AuditLogJpaRepository;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEventSink;
import com.shinhan.corebank.scheduledtransfer.adapter.out.persistence.ScheduledTransferJpaEntity;
import com.shinhan.corebank.scheduledtransfer.adapter.out.persistence.ScheduledTransferJpaRepository;
import com.shinhan.corebank.scheduledtransfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.TransferLookupResult;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 스케줄러 → 유스케이스 → 프로세서 → 실제 DB까지 끝까지 도는 테스트(PR #636 리뷰 R2).
// 멈춘 PROCESSING 이체를 실제 테이블에 심고, 실제 락 테이블과 실제 재확정 로직을 거친다.
// 모킹은 transfer 테이블 조회(TransferLookupPort)와 이벤트 sink 둘뿐이다.
// 클래스 레벨 @Transactional을 쓰지 않는다 - 락과 재확정이 REQUIRES_NEW로 따로 커밋된다.
class ScheduledTransferReconcileEndToEndTest extends IntegrationTestSupport {

    private static final String RECONCILE_JOB = "SCHEDULED_TRANSFER_RECONCILE";
    private static final String MAIN_JOB = "DAILY_TRANSFER_BATCH";
    private static final LocalDate SCHEDULED_DATE = LocalDate.of(2026, 3, 15);
    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();
    private static final AtomicLong ACCOUNT_SEQ = new AtomicLong();

    @MockitoBean
    TransferLookupPort transferLookupPort;

    @MockitoBean
    DomainEventSink domainEventSink;

    @Autowired
    ScheduledTransferReconcileScheduler scheduler;

    @Autowired
    ScheduledTransferJpaRepository scheduledTransferJpaRepository;

    @Autowired
    AuditLogJpaRepository auditLogJpaRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    Clock clock;

    @AfterEach
    void cleanUp() {
        transaction().executeWithoutResult(status -> {
            auditLogJpaRepository.deleteAll();
            scheduledTransferJpaRepository.deleteAll();
        });
        setLock(MAIN_JOB, false, LocalDateTime.now(clock));
        setLock(RECONCILE_JOB, false, LocalDateTime.now(clock));
        Mockito.reset(transferLookupPort, domainEventSink);
    }

    @Test
    @DisplayName("멈춘 PROCESSING 건에 transfer 행이 없으면 스케줄러가 FAILED로 확정하고 락을 반납한다")
    void stuckProcessingWithoutTransferRow_isFailed() {
        Long stuckId = seedProcessing();
        when(transferLookupPort.findBySourceAndDate(eq(stuckId), any())).thenReturn(Optional.empty());

        scheduler.reconcile();

        ScheduledTransferJpaEntity after =
                scheduledTransferJpaRepository.findById(stuckId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ScheduledTransferStatus.FAILED);
        assertThat(after.getFailureReason()).isEqualTo("실행 중 확인 불가로 재확정 배치가 오류 처리함");
        assertThat(isRunning(RECONCILE_JOB)).isFalse();
    }

    @Test
    @DisplayName("멈춘 PROCESSING 건에 SUCCESS 이체 행이 있으면 거래번호와 함께 SUCCESS로 확정한다")
    void stuckProcessingWithSuccessfulTransfer_isConfirmedAsSuccess() {
        Long stuckId = seedProcessing();
        when(transferLookupPort.findBySourceAndDate(eq(stuckId), any()))
                .thenReturn(Optional.of(
                        new TransferLookupResult("20260315BT0000000099", ProcessResultStatus.SUCCESS, null)));

        scheduler.reconcile();

        ScheduledTransferJpaEntity after =
                scheduledTransferJpaRepository.findById(stuckId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ScheduledTransferStatus.SUCCESS);
        assertThat(after.getTransactionNumber()).isEqualTo("20260315BT0000000099");
        assertThat(auditLogJpaRepository.findAll()).hasSize(1);
        assertThat(isRunning(RECONCILE_JOB)).isFalse();
    }

    @Test
    @DisplayName("메인 배치가 방금 시작되어 실행 중이면 멈춘 건으로 오판하지 않고 PROCESSING 그대로 둔다")
    void mainBatchRunning_leavesProcessingUntouched() {
        Long inFlightId = seedProcessing();
        setLock(MAIN_JOB, true, LocalDateTime.now(clock));

        scheduler.reconcile();

        ScheduledTransferJpaEntity after =
                scheduledTransferJpaRepository.findById(inFlightId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ScheduledTransferStatus.PROCESSING);
        verify(transferLookupPort, never()).findBySourceAndDate(any(), any());
    }

    @Test
    @DisplayName("메인 배치 락이 크래시로 7시간 넘게 남아 있어도 멈춘 PROCESSING 건을 확정한다")
    void staleMainBatchLock_stillConfirmsStuckProcessing() {
        Long stuckId = seedProcessing();
        when(transferLookupPort.findBySourceAndDate(eq(stuckId), any())).thenReturn(Optional.empty());
        setLock(MAIN_JOB, true, LocalDateTime.now(clock).minusHours(7));

        scheduler.reconcile();

        ScheduledTransferJpaEntity after =
                scheduledTransferJpaRepository.findById(stuckId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ScheduledTransferStatus.FAILED);
    }

    @Test
    @DisplayName("한 건의 조회가 예외를 던져도 나머지 멈춘 건은 확정된다")
    void oneFailingItem_doesNotBlockTheOthers() {
        Long brokenId = seedProcessing();
        Long okId = seedProcessing();
        when(transferLookupPort.findBySourceAndDate(eq(brokenId), any())).thenThrow(new IllegalStateException("조회 실패"));
        when(transferLookupPort.findBySourceAndDate(eq(okId), any())).thenReturn(Optional.empty());

        scheduler.reconcile();

        assertThat(scheduledTransferJpaRepository
                        .findById(brokenId)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(ScheduledTransferStatus.PROCESSING);
        assertThat(scheduledTransferJpaRepository.findById(okId).orElseThrow().getStatus())
                .isEqualTo(ScheduledTransferStatus.FAILED);
        assertThat(isRunning(RECONCILE_JOB)).isFalse();
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private Long seedProcessing() {
        return transaction().execute(status -> {
            Long customerId = insertCustomer();
            Long accountId = insertAccount(customerId);
            return scheduledTransferJpaRepository
                    .save(ScheduledTransferJpaEntity.builder()
                            .customerId(customerId)
                            .withdrawalAccountId(accountId)
                            .payeeBankCode("088")
                            .payeeAccountNumber("110987654321")
                            .payeeName("홍길동")
                            .amount(10_000L)
                            .scheduledDate(SCHEDULED_DATE)
                            .myPassbookMemo("메모")
                            .recipientPassbookMemo("받는메모")
                            .status(ScheduledTransferStatus.PROCESSING)
                            .registeredAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                            .build())
                    .getScheduledTransferId();
        });
    }

    private Long insertCustomer() {
        long seq = CUSTOMER_SEQ.incrementAndGet();
        entityManager
                .createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", "e2e" + seq)
                .setParameter("email", "e2e" + seq + "@test.com")
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("SELECT LAST_INSERT_ID()")
                        .getSingleResult())
                .longValue();
    }

    private Long insertAccount(Long customerId) {
        String accountNumber = String.format("%012d", 700_000_000_000L + ACCOUNT_SEQ.incrementAndGet());
        entityManager
                .createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, opened_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', 'ACTIVE', 'x', NOW(), NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("SELECT LAST_INSERT_ID()")
                        .getSingleResult())
                .longValue();
    }

    private void setLock(String jobName, boolean running, LocalDateTime updatedAt) {
        transaction().executeWithoutResult(status -> entityManager
                .createNativeQuery("UPDATE batch_execution_lock SET currently_running = :running,"
                        + " updated_at = :updatedAt WHERE job_name = :jobName")
                .setParameter("running", running)
                .setParameter("updatedAt", updatedAt)
                .setParameter("jobName", jobName)
                .executeUpdate());
    }

    private boolean isRunning(String jobName) {
        Object value = transaction().execute(status -> entityManager
                .createNativeQuery("SELECT currently_running FROM batch_execution_lock WHERE job_name = :jobName")
                .setParameter("jobName", jobName)
                .getSingleResult());
        return value instanceof Boolean flag ? flag : ((Number) value).intValue() != 0;
    }
}
