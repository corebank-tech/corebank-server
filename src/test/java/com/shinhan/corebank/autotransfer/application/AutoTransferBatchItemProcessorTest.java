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
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.audit.AuditLogJpaRepository;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 클래스 레벨 @Transactional을 일부러 안 쓴다 - saveProcessing()/completeProcessing()이
 * REQUIRES_NEW로 진짜 독립 커밋되는지 증명해야 하는데, 테스트 자체가 트랜잭션으로 묶여있으면
 * 그 안에서 일어난 REQUIRES_NEW 커밋이 진짜 영구적인지 확인할 수 없다(테스트 종료 시 다 같이
 * 롤백돼버리면 구분이 안 됨). 대신 각 테스트 끝나고 수동으로 정리한다.
 */
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
    }

    @Test
    @DisplayName("이체 실패 시: 회차가 ERROR로 확정되지만 다음 실행일은 그대로 갱신되고, 감사로그는 안 남는다")
    void completeProcessing_failure() {
        AutoTransferExecution saved = itemProcessor.saveProcessing(autoTransfer(), today);
        when(transferExecutionUseCase.execute(any())).thenReturn(TransferResult.builder()
                .status(ProcessResultStatus.ERROR)
                .errorCode("LMT0001")
                .errorMessage("잔액 부족")
                .build());

        itemProcessor.completeProcessing(autoTransfer(), saved, today);

        var executionAfter = executionRepository.findById(saved.getExecutionId()).orElseThrow();
        assertThat(executionAfter.getStatus()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(executionAfter.getFailureReason()).isEqualTo("잔액 부족");

        var autoTransferAfter = autoTransferJpaRepository.findById(autoTransferId).orElseThrow();
        assertThat(autoTransferAfter.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 4, 15));

        assertThat(auditLogJpaRepository.findAll()).isEmpty();
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
    @DisplayName("completeProcessing() 실패로 회차가 PROCESSING에 멈춘 채 배치를 재실행해도, uk_ate_dup에 막혀 이체가 다시 실행되지 않는다 (REQ-AUTO-017)")
    void executeDaily_rerunAfterStuckProcessing_doesNotDoubleExecute() {
        // 1차 실행: saveProcessing()은 성공(PROCESSING 즉시 커밋)하지만 completeProcessing()이
        // 실패해서 nextExecutionDate가 안 넘어간 채로 남는다 - "멈춘 배치"를 재현(재실행 런북 시나리오)
        when(transferExecutionUseCase.execute(any())).thenThrow(new RuntimeException("이체 실행 중 장애"));

        autoTransferBatchUseCase.executeDaily(today);

        var afterFirstRun = executionRepository.findAll();
        assertThat(afterFirstRun).hasSize(1);
        assertThat(afterFirstRun.get(0).getStatus()).isEqualTo(ProcessResultStatus.PROCESSING);

        // 2차 실행(재실행): nextExecutionDate가 여전히 today라 findDueForExecution()이 같은 건을
        // 다시 찾아내지만, saveProcessing()이 같은 (auto_transfer_id, execution_date)로 또 시도하다가
        // uk_ate_dup 유니크 제약에 막혀 "이미 처리 중"으로 보고 completeProcessing()을 다시 호출하지 않는다
        autoTransferBatchUseCase.executeDaily(today);

        verify(transferExecutionUseCase, times(1)).execute(any());
        assertThat(executionRepository.findAll()).hasSize(1);
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
