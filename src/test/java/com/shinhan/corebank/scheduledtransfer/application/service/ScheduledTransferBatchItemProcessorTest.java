package com.shinhan.corebank.scheduledtransfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.audit.AuditLogJpaRepository;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.scheduledtransfer.adapter.out.persistence.ScheduledTransferJpaEntity;
import com.shinhan.corebank.scheduledtransfer.adapter.out.persistence.ScheduledTransferJpaRepository;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferBatchUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.TransferLookupResult;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 클래스 레벨 @Transactional을 일부러 안 쓴다 - claim()/completeProcessing()이 REQUIRES_NEW로
 * 진짜 독립 커밋되는지 증명해야 하는데, 테스트 자체가 트랜잭션으로 묶여있으면 그 안에서 일어난
 * REQUIRES_NEW 커밋이 진짜 영구적인지 확인할 수 없다. 대신 각 테스트 끝나고 수동으로 정리한다.
 */
class ScheduledTransferBatchItemProcessorTest extends IntegrationTestSupport {

    @Autowired
    ScheduledTransferBatchItemProcessor itemProcessor;

    @Autowired
    ScheduledTransferBatchUseCase scheduledTransferBatchUseCase;

    @Autowired
    ScheduledTransferJpaRepository scheduledTransferJpaRepository;

    @Autowired
    AuditLogJpaRepository auditLogJpaRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    TransferExecutionUseCase transferExecutionUseCase;

    @MockitoBean
    TransferLookupPort transferLookupPort;

    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();
    private static final AtomicLong ACCOUNT_SEQ = new AtomicLong();
    private static final LocalDate SCHEDULED_DATE = LocalDate.of(2026, 3, 15);

    private Long customerId;
    private Long scheduledTransferId;

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void seedScheduledTransfer() {
        transactionTemplate().executeWithoutResult(status -> {
            customerId = insertCustomer();
            Long accountId = insertAccount(customerId);
            ScheduledTransferJpaEntity entity = scheduledTransferJpaRepository.save(ScheduledTransferJpaEntity.builder()
                    .customerId(customerId)
                    .withdrawalAccountId(accountId)
                    .payeeBankCode("088")
                    .payeeAccountNumber("110987654321")
                    .payeeName("홍길동")
                    .amount(10_000L)
                    .scheduledDate(SCHEDULED_DATE)
                    .myPassbookMemo("메모")
                    .recipientPassbookMemo("받는메모")
                    .status(ScheduledTransferStatus.WAITING)
                    .registeredAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                    .build());
            scheduledTransferId = entity.getScheduledTransferId();
        });
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate().executeWithoutResult(status -> {
            auditLogJpaRepository.deleteAll();
            scheduledTransferJpaRepository.deleteAll();
        });
    }

    private ScheduledTransfer reloadAsDomain() {
        ScheduledTransferJpaEntity entity =
                scheduledTransferJpaRepository.findById(scheduledTransferId).orElseThrow();
        return ScheduledTransfer.reconstitute(
                entity.getScheduledTransferId(),
                entity.getCustomerId(),
                entity.getWithdrawalAccountId(),
                entity.getPayeeBankCode(),
                entity.getPayeeAccountNumber(),
                entity.getPayeeName(),
                entity.getAmount(),
                entity.getScheduledDate(),
                entity.getMyPassbookMemo(),
                entity.getRecipientPassbookMemo(),
                entity.getStatus(),
                entity.getTransactionNumber(),
                entity.getRegisteredAt(),
                entity.getExecutedAt(),
                entity.getCanceledAt(),
                entity.getFailureReason(),
                entity.getVersion());
    }

    @Test
    @DisplayName("claim()은 WAITING 건을 PROCESSING으로 즉시 커밋한다")
    void claim_waitingRow_commitsProcessingImmediately() {
        boolean claimed = itemProcessor.claim(scheduledTransferId);

        assertThat(claimed).isTrue();
        assertThat(scheduledTransferJpaRepository
                        .findById(scheduledTransferId)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(ScheduledTransferStatus.PROCESSING);
    }

    @Test
    @DisplayName("이미 PROCESSING인 건에 claim()을 다시 호출하면 false를 반환한다 (중복 수행 방어)")
    void claim_alreadyProcessing_returnsFalse() {
        itemProcessor.claim(scheduledTransferId);

        boolean secondClaim = itemProcessor.claim(scheduledTransferId);

        assertThat(secondClaim).isFalse();
    }

    @Test
    @DisplayName("동일 예약건에 배치가 중복 수행돼도(executeDaily 두 번 호출) claim 경쟁으로 이체는 1회만 발생한다 "
            + "- 실제 서비스/영속성을 그대로 쓰고 TransferExecutionUseCase만 mock으로 둔다 (#184 DoD)")
    void executeDaily_duplicateBatchRun_executesTransferOnlyOnce() {
        when(transferExecutionUseCase.execute(any()))
                .thenReturn(TransferResult.builder()
                        .status(ProcessResultStatus.SUCCESS)
                        .transactionNumber("20260315BT0000000005")
                        .transferredAt(LocalDateTime.now())
                        .build());

        // 같은 예약건을 대상으로 executeDaily()를 두 번 호출해 "중복 배치 실행"을 재현한다.
        // 두 번째 호출의 findDueForExecution()은 여전히 이 건을 WAITING이 아닌 걸로 보지 않고 그대로 대상에 포함시킬 수 있지만
        // (조회 조건이 상태 변화를 실시간 반영 안 할 수 있음을 배제하지 않기 위해), claim()의 조건부 UPDATE가 실제 방어선이다.
        scheduledTransferBatchUseCase.executeDaily(SCHEDULED_DATE);
        scheduledTransferBatchUseCase.executeDaily(SCHEDULED_DATE);

        verify(transferExecutionUseCase, times(1)).execute(any());
        ScheduledTransferJpaEntity after =
                scheduledTransferJpaRepository.findById(scheduledTransferId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ScheduledTransferStatus.SUCCESS);
        assertThat(after.getTransactionNumber()).isEqualTo("20260315BT0000000005");
    }

    @Test
    @DisplayName("두 배치 실행이 findDueForExecution()으로 같은 WAITING 스냅샷을 각자 읽고 claim 경쟁을 해도 "
            + "(executeDaily 두 번 호출로는 위 테스트가 이미 재현하지만, 첫 실행이 완전히 끝나버리면 두 번째는 조회 조건에서 "
            + "걸러질 뿐 claim 자체가 경합하지 않을 수 있다 - 이 테스트는 claim() 경합 자체를 직접 재현) 이체는 1회만 발생한다")
    void claim_concurrentDuplicateRun_executesTransferOnlyOnce() {
        when(transferExecutionUseCase.execute(any()))
                .thenReturn(TransferResult.builder()
                        .status(ProcessResultStatus.SUCCESS)
                        .transactionNumber("20260315BT0000000006")
                        .transferredAt(LocalDateTime.now())
                        .build());

        // 두 실행이 저장 전에 각자 findDueForExecution()으로 같은 WAITING 스냅샷을 읽었다고 가정하고,
        // 실제 배치 오케스트레이션(processOne)과 동일하게 claim() -> 성공했을 때만 completeProcessing()을 따른다.
        ScheduledTransfer target = reloadAsDomain();

        boolean firstClaimed = itemProcessor.claim(target.getScheduledTransferId());
        if (firstClaimed) {
            itemProcessor.completeProcessing(target, SCHEDULED_DATE);
        }
        boolean secondClaimed = itemProcessor.claim(target.getScheduledTransferId());
        if (secondClaimed) {
            itemProcessor.completeProcessing(target, SCHEDULED_DATE);
        }

        assertThat(firstClaimed).isTrue();
        assertThat(secondClaimed).isFalse();
        verify(transferExecutionUseCase, times(1)).execute(any());
        ScheduledTransferJpaEntity after =
                scheduledTransferJpaRepository.findById(scheduledTransferId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ScheduledTransferStatus.SUCCESS);
        assertThat(after.getTransactionNumber()).isEqualTo("20260315BT0000000006");
    }

    @Test
    @DisplayName("completeProcessing()이 예외를 던져도, 이미 커밋된 claim()의 결과는 롤백되지 않는다")
    void completeProcessing_throws_doesNotRollbackEarlierClaim() {
        itemProcessor.claim(scheduledTransferId);
        when(transferExecutionUseCase.execute(any())).thenThrow(new RuntimeException("이체 실행 중 장애"));

        assertThatThrownBy(() -> itemProcessor.completeProcessing(reloadAsDomain(), SCHEDULED_DATE))
                .isInstanceOf(RuntimeException.class);

        assertThat(scheduledTransferJpaRepository
                        .findById(scheduledTransferId)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(ScheduledTransferStatus.PROCESSING);
    }

    @Test
    @DisplayName("이체 결과가 PROCESSING(응답 유실·타임아웃)이면 성공/실패로 단정하지 않고 예외를 던져 롤백시킨다")
    void completeProcessing_transferResultProcessing_throwsAndRollsBack() {
        itemProcessor.claim(scheduledTransferId);
        when(transferExecutionUseCase.execute(any()))
                .thenReturn(TransferResult.builder()
                        .status(ProcessResultStatus.PROCESSING)
                        .build());

        assertThatThrownBy(() -> itemProcessor.completeProcessing(reloadAsDomain(), SCHEDULED_DATE))
                .isInstanceOf(IllegalStateException.class);

        ScheduledTransferJpaEntity after =
                scheduledTransferJpaRepository.findById(scheduledTransferId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ScheduledTransferStatus.PROCESSING);
        assertThat(after.getTransactionNumber()).isNull();
        assertThat(auditLogJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("이체 성공 시: SUCCESS로 확정되고 감사로그가 남는다")
    void completeProcessing_success() {
        itemProcessor.claim(scheduledTransferId);
        when(transferExecutionUseCase.execute(any()))
                .thenReturn(TransferResult.builder()
                        .status(ProcessResultStatus.SUCCESS)
                        .transactionNumber("20260315BT0000000001")
                        .transferredAt(LocalDateTime.now())
                        .build());

        itemProcessor.completeProcessing(reloadAsDomain(), SCHEDULED_DATE);

        ScheduledTransferJpaEntity after =
                scheduledTransferJpaRepository.findById(scheduledTransferId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ScheduledTransferStatus.SUCCESS);
        assertThat(after.getTransactionNumber()).isEqualTo("20260315BT0000000001");
        assertThat(auditLogJpaRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("채번 이전 실패 시: FAILED로 확정되지만 거래번호가 없어 감사로그는 안 남는다 (재시도 없음)")
    void completeProcessing_failureBeforeSequencing_noAuditLogAndNoRetry() {
        itemProcessor.claim(scheduledTransferId);
        when(transferExecutionUseCase.execute(any()))
                .thenReturn(TransferResult.builder()
                        .status(ProcessResultStatus.ERROR)
                        .errorCode("TRF0001")
                        .errorMessage("출금계좌가 등록되지 않았습니다.")
                        .build());

        itemProcessor.completeProcessing(reloadAsDomain(), SCHEDULED_DATE);

        ScheduledTransferJpaEntity after =
                scheduledTransferJpaRepository.findById(scheduledTransferId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ScheduledTransferStatus.FAILED);
        assertThat(after.getFailureReason()).isEqualTo("출금계좌가 등록되지 않았습니다.");
        assertThat(after.getTransactionNumber()).isNull();
        assertThat(auditLogJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("채번 이후 실패(한도초과·잔액부족 등) 시: 거래번호가 함께 남고 실패 감사로그도 남는다")
    void completeProcessing_failureAfterSequencing_savesTransactionNumberAndAuditLog() {
        itemProcessor.claim(scheduledTransferId);
        when(transferExecutionUseCase.execute(any()))
                .thenReturn(TransferResult.builder()
                        .status(ProcessResultStatus.ERROR)
                        .transactionNumber("20260315BT0000000002")
                        .errorCode("TRF0002")
                        .errorMessage("잔액 부족")
                        .build());

        itemProcessor.completeProcessing(reloadAsDomain(), SCHEDULED_DATE);

        ScheduledTransferJpaEntity after =
                scheduledTransferJpaRepository.findById(scheduledTransferId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ScheduledTransferStatus.FAILED);
        assertThat(after.getFailureReason()).isEqualTo("잔액 부족");
        assertThat(after.getTransactionNumber()).isEqualTo("20260315BT0000000002");
        assertThat(auditLogJpaRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("재확정: transfer 테이블에 실제 거래가 없으면 FAILED로 확정한다")
    void reconcileStuckExecution_noLookupResult_marksFailed() {
        itemProcessor.claim(scheduledTransferId);
        when(transferLookupPort.findBySourceAndDate(scheduledTransferId, SCHEDULED_DATE))
                .thenReturn(Optional.empty());

        itemProcessor.reconcileStuckExecution(reloadAsDomain());

        ScheduledTransferJpaEntity after =
                scheduledTransferJpaRepository.findById(scheduledTransferId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ScheduledTransferStatus.FAILED);
        assertThat(after.getFailureReason()).isEqualTo("실행 중 확인 불가로 재확정 배치가 오류 처리함");
    }

    @Test
    @DisplayName("재확정: transfer 테이블에 SUCCESS 거래가 있으면 SUCCESS로 확정한다")
    void reconcileStuckExecution_successLookupResult_marksSuccess() {
        itemProcessor.claim(scheduledTransferId);
        when(transferLookupPort.findBySourceAndDate(scheduledTransferId, SCHEDULED_DATE))
                .thenReturn(Optional.of(
                        new TransferLookupResult("20260315BT0000000003", ProcessResultStatus.SUCCESS, null)));

        itemProcessor.reconcileStuckExecution(reloadAsDomain());

        ScheduledTransferJpaEntity after =
                scheduledTransferJpaRepository.findById(scheduledTransferId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ScheduledTransferStatus.SUCCESS);
        assertThat(after.getTransactionNumber()).isEqualTo("20260315BT0000000003");
        assertThat(auditLogJpaRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("재확정: transfer 테이블에 ERROR 거래가 있으면 그 실패사유로 FAILED 확정한다")
    void reconcileStuckExecution_errorLookupResult_marksFailedWithReason() {
        itemProcessor.claim(scheduledTransferId);
        when(transferLookupPort.findBySourceAndDate(scheduledTransferId, SCHEDULED_DATE))
                .thenReturn(Optional.of(
                        new TransferLookupResult("20260315BT0000000004", ProcessResultStatus.ERROR, "잔액 부족")));

        itemProcessor.reconcileStuckExecution(reloadAsDomain());

        ScheduledTransferJpaEntity after =
                scheduledTransferJpaRepository.findById(scheduledTransferId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ScheduledTransferStatus.FAILED);
        assertThat(after.getFailureReason()).isEqualTo("잔액 부족");
        assertThat(after.getTransactionNumber()).isEqualTo("20260315BT0000000004");
    }

    @Test
    @DisplayName("재확정이 동시에 두 번 수행돼도(같은 PROCESSING 스냅샷을 각자 읽음) 감사로그는 1건만 남는다")
    void reconcileStuckExecution_concurrentDuplicateRun_recordsAuditLogOnlyOnce() {
        itemProcessor.claim(scheduledTransferId);
        when(transferLookupPort.findBySourceAndDate(scheduledTransferId, SCHEDULED_DATE))
                .thenReturn(Optional.of(
                        new TransferLookupResult("20260315BT0000000099", ProcessResultStatus.SUCCESS, null)));
        // 두 재확정 실행이 저장 전에 각자 findAllProcessing()으로 같은 PROCESSING 스냅샷을 읽었다고 가정
        ScheduledTransfer firstSnapshot = reloadAsDomain();
        ScheduledTransfer secondSnapshot = reloadAsDomain();

        itemProcessor.reconcileStuckExecution(firstSnapshot);
        itemProcessor.reconcileStuckExecution(secondSnapshot);

        ScheduledTransferJpaEntity after =
                scheduledTransferJpaRepository.findById(scheduledTransferId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ScheduledTransferStatus.SUCCESS);
        assertThat(auditLogJpaRepository.findAll()).hasSize(1);
    }

    private Long insertCustomer() {
        long seq = CUSTOMER_SEQ.incrementAndGet();
        entityManager
                .createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", "u" + seq)
                .setParameter("email", "test" + seq + "@test.com")
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("SELECT LAST_INSERT_ID()")
                        .getSingleResult())
                .longValue();
    }

    private Long insertAccount(Long customerId) {
        String accountNumber = String.format("%012d", ACCOUNT_SEQ.incrementAndGet());
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
}
