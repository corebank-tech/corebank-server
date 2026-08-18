package com.shinhan.corebank.autotransfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferBatchUseCase;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferExecutionJpaRepository;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferJpaEntity;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferJpaRepository;
import com.shinhan.corebank.autotransfer.application.port.out.StuckExecution;
import com.shinhan.corebank.autotransfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.autotransfer.application.port.out.TransferLookupResult;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogJpaEntity;
import com.shinhan.corebank.common.audit.AuditLogJpaRepository;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 클래스 레벨 @Transactional을 일부러 안 쓴다 - saveProcessing()/completeProcessing()이
 * REQUIRES_NEW로 진짜 독립 커밋되는지 증명해야 하는데, 테스트 자체가 트랜잭션으로 묶여있으면
 * 그 안에서 일어난 REQUIRES_NEW 커밋이 진짜 영구적인지 확인할 수 없다(테스트 종료 시 다 같이
 * 롤백돼버리면 구분이 안 됨). 대신 각 테스트 끝나고 수동으로 정리한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class AutoTransferBatchItemProcessorTest extends IntegrationTestSupport {

    @Autowired
    AutoTransferBatchItemProcessor itemProcessor;

    @Autowired
    AutoTransferBatchUseCase autoTransferBatchUseCase;

    @Autowired
    AutoTransferExecutionJpaRepository executionRepository;

    @Autowired
    AutoTransferJpaRepository autoTransferJpaRepository;

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

    private Long customerId;
    private Long autoTransferId;
    private LocalDate today;
    private LocalDate endDate;

    // @BeforeEach/@AfterEach는 Spring 테스트 트랜잭션 지원 대상이 아니라서(@Test에만 적용됨),
    // AuditLogServiceTest와 동일하게 TransactionTemplate으로 직접 트랜잭션을 열고 닫는다.
    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void seedAutoTransfer() {
        today = LocalDate.of(2026, 3, 15);
        endDate = LocalDate.of(2027, 1, 1);
        transactionTemplate().executeWithoutResult(status -> {
            customerId = insertCustomer();
            Long accountId = insertAccount(customerId);
            AutoTransferJpaEntity entity = autoTransferJpaRepository.save(AutoTransferJpaEntity.builder()
                    .customerId(customerId)
                    .withdrawalAccountId(accountId)
                    .depositAccountNumber("110987654321")
                    .payeeName("홍길동")
                    .amount(10000L)
                    .cycleMonths(1)
                    .transferDay(15)
                    .startDate(LocalDate.of(2026, 1, 1))
                    .endDate(endDate)
                    .nextExecutionDate(today)
                    .myPassbookMemo("메모")
                    .recipientPassbookMemo("받는메모")
                    .status(AutoTransferStatus.NORMAL)
                    .registeredAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                    .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                    .build());
            autoTransferId = entity.getAutoTransferId();
        });
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate().executeWithoutResult(status -> {
            auditLogJpaRepository.deleteAll();
            executionRepository.deleteAll();
            autoTransferJpaRepository.deleteAll();
        });
    }

    private AutoTransfer autoTransfer() {
        return AutoTransfer.reconstitute(
                autoTransferId, customerId, autoTransferJpaRepository.findById(autoTransferId).orElseThrow().getWithdrawalAccountId(),
                "110987654321", "홍길동",
                10000L, 1, 15,
                LocalDate.of(2026, 1, 1), endDate, today,
                "메모", "받는메모", AutoTransferStatus.NORMAL,
                LocalDateTime.of(2026, 1, 1, 0, 0), null, LocalDateTime.of(2026, 1, 1, 0, 0), 0L);
    }

    @Test
    @DisplayName("saveProcessing()은 PROCESSING 행을 즉시 커밋한다")
    void saveProcessing_commitsImmediately() {
        AutoTransferExecution saved = itemProcessor.saveProcessing(autoTransfer(), today);

        assertThat(saved.getExecutionId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(ProcessResultStatus.PROCESSING);
        assertThat(executionRepository.findById(saved.getExecutionId())).isPresent();
    }

    @Test
    @DisplayName("같은 자동이체·같은 실행일로 saveProcessing()을 두 번 호출하면 두 번째는 유니크 제약 위반으로 실패한다")
    void saveProcessing_duplicate_throws() {
        itemProcessor.saveProcessing(autoTransfer(), today);

        assertThatThrownBy(() -> itemProcessor.saveProcessing(autoTransfer(), today))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("completeProcessing()이 예외를 던져도, 이미 커밋된 saveProcessing()의 결과는 롤백되지 않는다")
    void completeProcessing_throws_doesNotRollbackEarlierSaveProcessing() {
        AutoTransferExecution saved = itemProcessor.saveProcessing(autoTransfer(), today);
        when(transferExecutionUseCase.execute(any())).thenThrow(new RuntimeException("이체 실행 중 장애"));

        assertThatThrownBy(() -> itemProcessor.completeProcessing(autoTransfer(), saved, today))
                .isInstanceOf(RuntimeException.class);

        // completeProcessing()은 통째로 롤백됐어야 하지만, saveProcessing()은 별도 트랜잭션이라 살아있어야 한다
        AutoTransferExecution stillThere = executionRepository.findById(saved.getExecutionId())
                .map(e -> AutoTransferExecution.reconstitute(e.getExecutionId(), e.getExecutionDate(), e.getAmount(),
                        e.getStatus(), e.getTransactionNumber(), e.getFailureReason(), e.getExecutedAt()))
                .orElseThrow(() -> new AssertionError("PROCESSING 행이 롤백으로 사라짐 - REQUIRES_NEW 격리 실패"));
        assertThat(stillThere.getStatus()).isEqualTo(ProcessResultStatus.PROCESSING);
    }

    @Test
    @DisplayName("이체 결과가 PROCESSING(응답 유실·타임아웃)이면 성공/실패로 단정하지 않고 예외를 던져 롤백시킨다")
    void completeProcessing_transferResultProcessing_throwsAndRollsBack() {
        AutoTransferExecution saved = itemProcessor.saveProcessing(autoTransfer(), today);
        when(transferExecutionUseCase.execute(any())).thenReturn(TransferResult.builder()
                .status(ProcessResultStatus.PROCESSING)
                .build());

        assertThatThrownBy(() -> itemProcessor.completeProcessing(autoTransfer(), saved, today))
                .isInstanceOf(IllegalStateException.class);

        // completeProcessing() 전체가 롤백되어 회차는 여전히 PROCESSING이어야 하고,
        // markSuccess/markError 어느 쪽으로도 확정되면 안 된다 - 재확정 배치(4주차)의 몫으로 남겨둠
        var executionAfter = executionRepository.findById(saved.getExecutionId()).orElseThrow();
        assertThat(executionAfter.getStatus()).isEqualTo(ProcessResultStatus.PROCESSING);
        assertThat(executionAfter.getTransactionNumber()).isNull();
        assertThat(executionAfter.getFailureReason()).isNull();

        var autoTransferAfter = autoTransferJpaRepository.findById(autoTransferId).orElseThrow();
        assertThat(autoTransferAfter.getNextExecutionDate()).isEqualTo(today);

        assertThat(auditLogJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("이체 성공 시: 회차가 SUCCESS로 확정되고, 다음 실행일이 갱신되고, 감사로그가 남는다")
    void completeProcessing_success() {
        AutoTransferExecution saved = itemProcessor.saveProcessing(autoTransfer(), today);
        when(transferExecutionUseCase.execute(any())).thenReturn(TransferResult.builder()
                .status(ProcessResultStatus.SUCCESS)
                .transactionNumber("20260315BT0000000001")
                .transferredAt(LocalDateTime.now())
                .withdrawalBalanceAfter(90000L)
                .build());

        itemProcessor.completeProcessing(autoTransfer(), saved, today);

        var executionAfter = executionRepository.findById(saved.getExecutionId()).orElseThrow();
        assertThat(executionAfter.getStatus()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(executionAfter.getTransactionNumber()).isEqualTo("20260315BT0000000001");

        var autoTransferAfter = autoTransferJpaRepository.findById(autoTransferId).orElseThrow();
        assertThat(autoTransferAfter.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(autoTransferAfter.getStatus()).isEqualTo(AutoTransferStatus.NORMAL);

        assertThat(auditLogJpaRepository.findAll()).hasSize(1);
        assertThat(auditLogJpaRepository.findAll().get(0).getDetail()).doesNotContainKey("errorCode");
    }

    @Test
    @DisplayName("채번 이전 실패 시: 회차가 ERROR로 확정되지만 다음 실행일은 그대로 갱신되고, 거래번호가 없어 감사로그는 안 남는다")
    void completeProcessing_failureBeforeSequencing_noAuditLog() {
        AutoTransferExecution saved = itemProcessor.saveProcessing(autoTransfer(), today);
        when(transferExecutionUseCase.execute(any())).thenReturn(TransferResult.builder()
                .status(ProcessResultStatus.ERROR)
                .errorCode("TRF0001")
                .errorMessage("출금계좌가 등록되지 않았습니다.")
                .build());

        itemProcessor.completeProcessing(autoTransfer(), saved, today);

        var executionAfter = executionRepository.findById(saved.getExecutionId()).orElseThrow();
        assertThat(executionAfter.getStatus()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(executionAfter.getFailureReason()).isEqualTo("출금계좌가 등록되지 않았습니다.");
        assertThat(executionAfter.getTransactionNumber()).isNull();

        var autoTransferAfter = autoTransferJpaRepository.findById(autoTransferId).orElseThrow();
        assertThat(autoTransferAfter.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 4, 15));

        assertThat(auditLogJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("채번 이후 실패(한도초과·잔액부족 등) 시: 실행이력에 거래번호가 함께 남고, 실패 감사로그도 남는다")
    void completeProcessing_failureAfterSequencing_savesTransactionNumberAndAuditLog() {
        AutoTransferExecution saved = itemProcessor.saveProcessing(autoTransfer(), today);
        when(transferExecutionUseCase.execute(any())).thenReturn(TransferResult.builder()
                .status(ProcessResultStatus.ERROR)
                .transactionNumber("20260315BT0000000002")
                .errorCode("LMT0001")
                .errorMessage("잔액 부족")
                .build());

        itemProcessor.completeProcessing(autoTransfer(), saved, today);

        var executionAfter = executionRepository.findById(saved.getExecutionId()).orElseThrow();
        assertThat(executionAfter.getStatus()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(executionAfter.getFailureReason()).isEqualTo("잔액 부족");
        assertThat(executionAfter.getTransactionNumber()).isEqualTo("20260315BT0000000002");

        assertThat(auditLogJpaRepository.findAll()).hasSize(1);
        var auditLog = auditLogJpaRepository.findAll().get(0);
        assertThat(auditLog.getResult()).isEqualTo("FAILURE");
        assertThat(auditLog.getDetail()).containsEntry("errorCode", "LMT0001");
    }

    @Test
    @DisplayName("다음 실행일이 종료일을 넘으면 자동이체가 만료(EXPIRED)된다")
    void completeProcessing_pastEndDate_expiresAutoTransfer() {
        // endDate를 이번 회차 실행일 다음달 이전으로 좁혀서, advanceNextExecutionDate() 이후 만료되게 한다
        autoTransferJpaRepository.save(AutoTransferJpaEntity.builder()
                .autoTransferId(autoTransferId)
                .customerId(customerId)
                .withdrawalAccountId(autoTransferJpaRepository.findById(autoTransferId).orElseThrow().getWithdrawalAccountId())
                .depositAccountNumber("110987654321")
                .payeeName("홍길동")
                .amount(10000L)
                .cycleMonths(1)
                .transferDay(15)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 3, 20))
                .nextExecutionDate(today)
                .myPassbookMemo("메모")
                .recipientPassbookMemo("받는메모")
                .status(AutoTransferStatus.NORMAL)
                .registeredAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .version(0L)
                .build());
        // 위 save()가 UPDATE라 DB의 version이 0 -> 1로 올라간다 - 아래 도메인 객체는 이 최신 버전을 들고 있어야
        // completeProcessing()의 저장 시도가 낙관적 락 충돌 없이 성공한다
        AutoTransferJpaEntity afterManualSave = autoTransferJpaRepository.findById(autoTransferId).orElseThrow();
        AutoTransfer domainWithNearEndDate = AutoTransfer.reconstitute(
                autoTransferId, customerId, afterManualSave.getWithdrawalAccountId(),
                "110987654321", "홍길동", 10000L, 1, 15,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 20), today,
                "메모", "받는메모", AutoTransferStatus.NORMAL,
                LocalDateTime.of(2026, 1, 1, 0, 0), null, LocalDateTime.of(2026, 1, 1, 0, 0), afterManualSave.getVersion());

        AutoTransferExecution saved = itemProcessor.saveProcessing(domainWithNearEndDate, today);
        when(transferExecutionUseCase.execute(any())).thenReturn(TransferResult.builder()
                .status(ProcessResultStatus.SUCCESS)
                .transactionNumber("20260315BT0000000002")
                .transferredAt(LocalDateTime.now())
                .withdrawalBalanceAfter(90000L)
                .build());

        itemProcessor.completeProcessing(domainWithNearEndDate, saved, today);

        var autoTransferAfter = autoTransferJpaRepository.findById(autoTransferId).orElseThrow();
        assertThat(autoTransferAfter.getStatus()).isEqualTo(AutoTransferStatus.EXPIRED);
    }

    @Test
    @DisplayName("completeProcessing() 저장 시점에 낙관적 락 충돌이 나면 삼키지 않고 그대로 전파하고, 고객이 동시에 바꾼 값은 덮어써지지 않는다")
    void completeProcessing_optimisticLockConflict_propagatesAndPreservesConcurrentChange() {
        AutoTransferExecution saved = itemProcessor.saveProcessing(autoTransfer(), today);
        when(transferExecutionUseCase.execute(any())).thenReturn(TransferResult.builder()
                .status(ProcessResultStatus.SUCCESS)
                .transactionNumber("20260315BT0000000004")
                .transferredAt(LocalDateTime.now())
                .withdrawalBalanceAfter(90000L)
                .build());

        // 고객이 배치와 동시에 금액을 20000원으로 바꿔서 먼저 커밋됐다고 가정 - DB의 version이 0 -> 1로 올라간다
        autoTransferJpaRepository.save(AutoTransferJpaEntity.builder()
                .autoTransferId(autoTransferId)
                .customerId(customerId)
                .withdrawalAccountId(autoTransferJpaRepository.findById(autoTransferId).orElseThrow().getWithdrawalAccountId())
                .depositAccountNumber("110987654321")
                .payeeName("홍길동")
                .amount(20000L)
                .cycleMonths(1)
                .transferDay(15)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(endDate)
                .nextExecutionDate(today)
                .myPassbookMemo("메모")
                .recipientPassbookMemo("받는메모")
                .status(AutoTransferStatus.NORMAL)
                .registeredAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .version(0L)
                .build());

        // 배치는 위 변경을 모른 채(autoTransfer() 헬퍼가 항상 version=0인 stale 객체를 리턴) completeProcessing()을 시도한다
        assertThatThrownBy(() -> itemProcessor.completeProcessing(autoTransfer(), saved, today))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // completeProcessing() 전체가 롤백됐으므로, 고객이 동시에 바꾼 금액(20000)이 배치의 stale 값(10000)으로
        // 덮어써지지 않고 그대로 보존돼야 한다 - 이게 이번 R1-a 작업이 막으려던 바로 그 문제
        var autoTransferAfter = autoTransferJpaRepository.findById(autoTransferId).orElseThrow();
        assertThat(autoTransferAfter.getAmount()).isEqualTo(20000L);
        assertThat(autoTransferAfter.getNextExecutionDate()).isEqualTo(today);

        // completeProcessing() 전체가 롤백됐어야 하므로, AutoTransfer 저장 전에 이미 SUCCESS로
        // 저장 시도했던 회차(processingExecution)도 같이 롤백되어 PROCESSING 상태 그대로여야 한다 -
        // 트랜잭션 경계가 깨져서 회차만 SUCCESS로 확정된 채 남는 걸 놓치지 않기 위한 검증
        AutoTransferExecution executionAfter = executionRepository.findById(saved.getExecutionId())
                .map(e -> AutoTransferExecution.reconstitute(e.getExecutionId(), e.getExecutionDate(), e.getAmount(),
                        e.getStatus(), e.getTransactionNumber(), e.getFailureReason(), e.getExecutedAt()))
                .orElseThrow();
        assertThat(executionAfter.getStatus()).isEqualTo(ProcessResultStatus.PROCESSING);
        assertThat(executionAfter.getTransactionNumber()).isNull();
        assertThat(auditLogJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("execute()가 실구현처럼 독립 커밋(REQUIRES_NEW)하면, completeProcessing() 이후 단계 실패 시 이체 마커는 남고 배치 기록만 롤백된다 - 재시도는 execute()를 다시 부르지 않고 멈춘 회차를 조용히 건너뛴다 (R1-b)")
    void completeProcessing_dangerousIndependentlyCommittingExecute_createsInconsistencyAndRetryDoesNotReExecute() {
        AutoTransferExecution saved = itemProcessor.saveProcessing(autoTransfer(), today);

        // "위험한" execute() 대역: 실구현이 자기 자신도 REQUIRES_NEW로 독립 커밋한다고 가정하고,
        // "돈이 나갔다"를 흉내내는 마커를 완전히 별도 트랜잭션에 즉시 커밋한다.
        // transactionTemplate()은 기본 전파(REQUIRED)라서, 이미 completeProcessing()의
        // REQUIRES_NEW 트랜잭션 안에서 부르면 새 트랜잭션을 안 만들고 거기 합류해버린다 -
        // 그러면 나중에 completeProcessing()이 롤백될 때 이 마커도 같이 사라져서 "독립 커밋"을
        // 재현하지 못한다. 여기서는 반드시 REQUIRES_NEW를 명시해야 한다.
        TransactionTemplate requiresNewForMarker = new TransactionTemplate(transactionManager);
        requiresNewForMarker.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        when(transferExecutionUseCase.execute(any())).thenAnswer(invocation -> {
            requiresNewForMarker.executeWithoutResult(status ->
                    auditLogJpaRepository.save(AuditLogJpaEntity.of(null, "20260315BT0000000099",
                            AuditEventType.AUTO_TRANSFER, "127.0.0.1", true, null, LocalDateTime.now())));
            return TransferResult.builder()
                    .status(ProcessResultStatus.SUCCESS)
                    .transactionNumber("20260315BT0000000099")
                    .transferredAt(LocalDateTime.now())
                    .withdrawalBalanceAfter(90000L)
                    .build();
        });

        // 고객이 배치와 동시에 값을 바꿔서 먼저 커밋됐다고 가정 - completeProcessing()의 이후 단계
        // (autoTransferPersistencePort.save())가 낙관적 락 충돌로 실패하도록 만든다
        autoTransferJpaRepository.save(AutoTransferJpaEntity.builder()
                .autoTransferId(autoTransferId)
                .customerId(customerId)
                .withdrawalAccountId(autoTransferJpaRepository.findById(autoTransferId).orElseThrow().getWithdrawalAccountId())
                .depositAccountNumber("110987654321")
                .payeeName("홍길동")
                .amount(20000L)
                .cycleMonths(1)
                .transferDay(15)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(endDate)
                .nextExecutionDate(today)
                .myPassbookMemo("메모")
                .recipientPassbookMemo("받는메모")
                .status(AutoTransferStatus.NORMAL)
                .registeredAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .version(0L)
                .build());

        // 1차 실행: execute()는 성공(마커 독립 커밋)하지만, 그 이후 단계가 낙관적 락 충돌로 실패해
        // completeProcessing() 전체가 롤백된다
        assertThatThrownBy(() -> itemProcessor.completeProcessing(autoTransfer(), saved, today))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // "돈은 나갔다"(마커는 독립 커밋되어 살아있음) vs "배치 쪽 기록은 롤백됨"(회차가 여전히 PROCESSING) - 불일치 재현
        assertThat(auditLogJpaRepository.findAll())
                .extracting(AuditLogJpaEntity::getTransactionNumber)
                .containsExactly("20260315BT0000000099");
        AutoTransferExecution stillProcessing = executionRepository.findById(saved.getExecutionId())
                .map(e -> AutoTransferExecution.reconstitute(e.getExecutionId(), e.getExecutionDate(), e.getAmount(),
                        e.getStatus(), e.getTransactionNumber(), e.getFailureReason(), e.getExecutedAt()))
                .orElseThrow();
        assertThat(stillProcessing.getStatus()).isEqualTo(ProcessResultStatus.PROCESSING);

        // findDueForExecution()이 이 건을 여전히 대상으로 볼 수 있는 상태인지 확인 - 재시도에서
        // execute()가 안 불린 이유가 "애초에 대상에서 제외돼서"가 아님을 배제한다
        var stillEligible = autoTransferJpaRepository.findById(autoTransferId).orElseThrow();
        assertThat(stillEligible.getStatus()).isEqualTo(AutoTransferStatus.NORMAL);
        assertThat(stillEligible.getNextExecutionDate()).isEqualTo(today);

        // saveProcessing()을 직접 다시 호출하면 실제로 uk_ate_dup 유니크 제약에 막히는지 직접 증명한다 -
        // 재시도에서 execute()가 안 불린 게 정확히 이 제약 때문임을 추측이 아니라 사실로 확인한다
        assertThatThrownBy(() -> itemProcessor.saveProcessing(autoTransfer(), today))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(((DataIntegrityViolationException) e).getMostSpecificCause().getMessage())
                        .contains("uk_ate_dup"));

        // 2차 실행(재실행): nextExecutionDate가 안 넘어가서 findDueForExecution()이 같은 건을 다시 찾아내지만,
        // saveProcessing()이 위에서 직접 증명한 uk_ate_dup 제약에 막혀 completeProcessing()이 다시 호출되지 않는다
        // - execute()도 다시 불리지 않는다. "중복 송금"이 아니라 "멈춘 건이 계속 방치된다"는 게 실제 위험이다.
        autoTransferBatchUseCase.executeDaily(today);

        verify(transferExecutionUseCase, times(1)).execute(any());
        assertThat(auditLogJpaRepository.findAll()).hasSize(1);
        assertThat(executionRepository.findAll()).hasSize(1);
        assertThat(executionRepository.findAll().get(0).getStatus()).isEqualTo(ProcessResultStatus.PROCESSING);
    }

    @Test
    @DisplayName("재확정: transfer 테이블에 SUCCESS 행이 있으면 그 거래번호로 확정한다")
    void reconcileStuckExecution_found_success_marksSuccess() {
        AutoTransferExecution saved = itemProcessor.saveProcessing(autoTransfer(), today);
        when(transferLookupPort.findBySourceAndDate(autoTransferId, today))
                .thenReturn(Optional.of(new TransferLookupResult(
                        "20260315BT0000000001", ProcessResultStatus.SUCCESS, null)));

        itemProcessor.reconcileStuckExecution(new StuckExecution(autoTransfer(), saved));

        var executionAfter = executionRepository.findById(saved.getExecutionId()).orElseThrow();
        assertThat(executionAfter.getStatus()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(executionAfter.getTransactionNumber()).isEqualTo("20260315BT0000000001");
    }

    @Test
    @DisplayName("재확정: transfer 테이블에 ERROR 행이 있으면 그 행의 실패사유·거래번호를 그대로 확정한다")
    void reconcileStuckExecution_found_error_marksErrorWithActualReason() {
        AutoTransferExecution saved = itemProcessor.saveProcessing(autoTransfer(), today);
        when(transferLookupPort.findBySourceAndDate(autoTransferId, today))
                .thenReturn(Optional.of(new TransferLookupResult(
                        "20260315BT0000000002", ProcessResultStatus.ERROR, "잔액 부족")));

        itemProcessor.reconcileStuckExecution(new StuckExecution(autoTransfer(), saved));

        var executionAfter = executionRepository.findById(saved.getExecutionId()).orElseThrow();
        assertThat(executionAfter.getStatus()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(executionAfter.getFailureReason()).isEqualTo("잔액 부족");
        assertThat(executionAfter.getTransactionNumber()).isEqualTo("20260315BT0000000002");
    }

    @Test
    @DisplayName("재확정: transfer 테이블에 행이 없으면 고정 사유로 ERROR 확정하고 거래번호는 null이다")
    void reconcileStuckExecution_notFound_marksErrorWithFixedReason() {
        AutoTransferExecution saved = itemProcessor.saveProcessing(autoTransfer(), today);
        when(transferLookupPort.findBySourceAndDate(autoTransferId, today))
                .thenReturn(Optional.empty());

        itemProcessor.reconcileStuckExecution(new StuckExecution(autoTransfer(), saved));

        var executionAfter = executionRepository.findById(saved.getExecutionId()).orElseThrow();
        assertThat(executionAfter.getStatus()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(executionAfter.getFailureReason()).isEqualTo("실행 중 확인 불가로 재확정 배치가 오류 처리함");
        assertThat(executionAfter.getTransactionNumber()).isNull();
    }

    @Test
    @DisplayName("reconcileStuckExecutions()는 findAllProcessing()이 찾은 모든 회차를 각각 재확정한다")
    void reconcileStuckExecutions_reconcilesEveryStuckExecution() {
        itemProcessor.saveProcessing(autoTransfer(), today);
        when(transferLookupPort.findBySourceAndDate(autoTransferId, today))
                .thenReturn(Optional.of(new TransferLookupResult(
                        "20260315BT0000000003", ProcessResultStatus.SUCCESS, null)));

        autoTransferBatchUseCase.reconcileStuckExecutions(today);

        assertThat(executionRepository.findAll()).hasSize(1);
        assertThat(executionRepository.findAll().get(0).getStatus()).isEqualTo(ProcessResultStatus.SUCCESS);
    }

    @Test
    @DisplayName("재확정 시에도 completeProcessing()과 동일하게 nextExecutionDate가 다음 주기로 갱신된다")
    void reconcileStuckExecution_advancesNextExecutionDate() {
        AutoTransferExecution saved = itemProcessor.saveProcessing(autoTransfer(), today);
        when(transferLookupPort.findBySourceAndDate(autoTransferId, today))
                .thenReturn(Optional.of(new TransferLookupResult(
                        "20260315BT0000000004", ProcessResultStatus.SUCCESS, null)));

        itemProcessor.reconcileStuckExecution(new StuckExecution(autoTransfer(), saved));

        var autoTransferAfter = autoTransferJpaRepository.findById(autoTransferId).orElseThrow();
        assertThat(autoTransferAfter.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(autoTransferAfter.getStatus()).isEqualTo(AutoTransferStatus.NORMAL);
    }

    @Test
    @DisplayName("재확정으로 다음 실행일이 종료일을 넘으면 자동이체가 만료(EXPIRED)된다")
    void reconcileStuckExecution_pastEndDate_expiresAutoTransfer() {
        // endDate를 이번 회차 실행일 다음달 이전으로 좁혀서, advanceNextExecutionDate() 이후 만료되게 한다
        autoTransferJpaRepository.save(AutoTransferJpaEntity.builder()
                .autoTransferId(autoTransferId)
                .customerId(customerId)
                .withdrawalAccountId(autoTransferJpaRepository.findById(autoTransferId).orElseThrow().getWithdrawalAccountId())
                .depositAccountNumber("110987654321")
                .payeeName("홍길동")
                .amount(10000L)
                .cycleMonths(1)
                .transferDay(15)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 3, 20))
                .nextExecutionDate(today)
                .myPassbookMemo("메모")
                .recipientPassbookMemo("받는메모")
                .status(AutoTransferStatus.NORMAL)
                .registeredAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .version(0L)
                .build());
        AutoTransferJpaEntity afterManualSave = autoTransferJpaRepository.findById(autoTransferId).orElseThrow();
        AutoTransfer domainWithNearEndDate = AutoTransfer.reconstitute(
                autoTransferId, customerId, afterManualSave.getWithdrawalAccountId(),
                "110987654321", "홍길동", 10000L, 1, 15,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 20), today,
                "메모", "받는메모", AutoTransferStatus.NORMAL,
                LocalDateTime.of(2026, 1, 1, 0, 0), null, LocalDateTime.of(2026, 1, 1, 0, 0), afterManualSave.getVersion());

        AutoTransferExecution saved = itemProcessor.saveProcessing(domainWithNearEndDate, today);
        when(transferLookupPort.findBySourceAndDate(autoTransferId, today))
                .thenReturn(Optional.of(new TransferLookupResult(
                        "20260315BT0000000005", ProcessResultStatus.SUCCESS, null)));

        itemProcessor.reconcileStuckExecution(new StuckExecution(domainWithNearEndDate, saved));

        var autoTransferAfter = autoTransferJpaRepository.findById(autoTransferId).orElseThrow();
        assertThat(autoTransferAfter.getStatus()).isEqualTo(AutoTransferStatus.EXPIRED);
    }

    @Test
    @DisplayName("같은 자동이체가 재확정으로 3회 연속 ERROR가 되면 WARN 로그를 남긴다")
    void reconcileStuckExecution_threeConsecutiveFailures_logsWarn(CapturedOutput output) {
        when(transferLookupPort.findBySourceAndDate(any(), any())).thenReturn(Optional.empty());

        reconcileOnDate(today.minusDays(2));
        reconcileOnDate(today.minusDays(1));
        reconcileOnDate(today);

        assertThat(output).contains("자동이체 연속 실패 감지").contains("autoTransferId=" + autoTransferId);
    }

    @Test
    @DisplayName("최근 3건 중 하나라도 SUCCESS면 연속 실패로 보지 않고 WARN 로그를 남기지 않는다")
    void reconcileStuckExecution_notAllRecentFailed_doesNotLogWarn(CapturedOutput output) {
        when(transferLookupPort.findBySourceAndDate(autoTransferId, today.minusDays(2)))
                .thenReturn(Optional.of(new TransferLookupResult("20260313BT0000000001", ProcessResultStatus.SUCCESS, null)));
        when(transferLookupPort.findBySourceAndDate(autoTransferId, today.minusDays(1))).thenReturn(Optional.empty());
        when(transferLookupPort.findBySourceAndDate(autoTransferId, today)).thenReturn(Optional.empty());

        reconcileOnDate(today.minusDays(2));
        reconcileOnDate(today.minusDays(1));
        reconcileOnDate(today);

        assertThat(output).doesNotContain("자동이체 연속 실패 감지");
    }

    // autoTransfer()는 version을 0L로 고정해서 반환하는데, reconcileStuckExecution()이
    // 매번 autoTransferPersistencePort.save()로 버전을 올리기 때문에 이 헬퍼를 여러 번
    // 부르는 테스트(연속 실패 시나리오)에서는 DB의 최신 버전을 매번 다시 읽어와야 한다.
    private void reconcileOnDate(LocalDate date) {
        AutoTransferJpaEntity entity = autoTransferJpaRepository.findById(autoTransferId).orElseThrow();
        AutoTransfer current = AutoTransfer.reconstitute(
                autoTransferId, customerId, entity.getWithdrawalAccountId(),
                "110987654321", "홍길동", 10000L, 1, 15,
                LocalDate.of(2026, 1, 1), endDate, entity.getNextExecutionDate(),
                "메모", "받는메모", entity.getStatus(),
                LocalDateTime.of(2026, 1, 1, 0, 0), null, LocalDateTime.of(2026, 1, 1, 0, 0), entity.getVersion());

        AutoTransferExecution saved = itemProcessor.saveProcessing(current, date);
        itemProcessor.reconcileStuckExecution(new StuckExecution(current, saved));
    }

    private Long insertCustomer() {
        long seq = CUSTOMER_SEQ.incrementAndGet();
        String userId = "u" + seq;
        String email = "test" + seq + "@test.com";
        entityManager.createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", userId)
                .setParameter("email", email)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private Long insertAccount(Long customerId) {
        String accountNumber = String.format("%012d", ACCOUNT_SEQ.incrementAndGet());
        entityManager.createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, opened_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', 'ACTIVE', 'x', NOW(), NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }
}
