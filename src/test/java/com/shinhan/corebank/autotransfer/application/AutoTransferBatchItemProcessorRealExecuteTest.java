package com.shinhan.corebank.autotransfer.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferExecutionJpaRepository;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferJpaEntity;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferJpaRepository;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.audit.AuditLogJpaRepository;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * r1b_execute_safety_verification.md 작업 4번: transfer 팀 실구현(feat/124, PR #132) merge 후,
 * TransferExecutionUseCase를 @MockitoBean으로 대체하지 않고 real TransferExecutionService
 * (@Primary)를 그대로 주입받아 completeProcessing()을 돌려서 실제로 안전한지 확인한다.
 *
 * <p>클래스 레벨 @Transactional을 안 쓰는 이유는 AutoTransferBatchItemProcessorTest와 동일 —
 * saveProcessing()/completeProcessing()이 REQUIRES_NEW로 진짜 독립 커밋되는지 봐야 한다.
 */
class AutoTransferBatchItemProcessorRealExecuteTest extends IntegrationTestSupport {

    @Autowired
    AutoTransferBatchItemProcessor itemProcessor;

    @Autowired
    TransferExecutionUseCase transferExecutionUseCase;

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

    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();
    private static final AtomicLong ACCOUNT_SEQ = new AtomicLong();

    private Long customerId;
    private Long autoTransferId;
    private LocalDate today;

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void seedAutoTransfer() {
        today = LocalDate.of(2026, 3, 15);
        transactionTemplate().executeWithoutResult(status -> {
            customerId = insertCustomer();
            Long withdrawalAccountId = insertAccount(customerId);
            AutoTransferJpaEntity entity = autoTransferJpaRepository.save(AutoTransferJpaEntity.builder()
                    .customerId(customerId)
                    .withdrawalAccountId(withdrawalAccountId)
                    .depositAccountNumber("110000000099") // 실제로 존재할 필요 없음 - 출금계좌 검증에서 먼저 막힘
                    .payeeName("홍길동")
                    .amount(10000L)
                    .cycleMonths(1)
                    .transferDay(15)
                    .startDate(LocalDate.of(2026, 1, 1))
                    .endDate(LocalDate.of(2027, 1, 1))
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
            // account가 customer를 FK로 참조하므로 account를 먼저 지운다
            entityManager.createNativeQuery("DELETE FROM account WHERE customer_id = :id")
                    .setParameter("id", customerId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM customer WHERE customer_id = :id")
                    .setParameter("id", customerId).executeUpdate();
        });
    }

    private AutoTransfer autoTransfer() {
        AutoTransferJpaEntity e = autoTransferJpaRepository.findById(autoTransferId).orElseThrow();
        return AutoTransfer.reconstitute(
                autoTransferId, customerId, e.getWithdrawalAccountId(),
                e.getDepositAccountNumber(), "홍길동",
                10000L, 1, 15,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), today,
                "메모", "받는메모", AutoTransferStatus.NORMAL,
                LocalDateTime.of(2026, 1, 1, 0, 0), null, LocalDateTime.of(2026, 1, 1, 0, 0), 0L);
    }

    @Test
    @DisplayName("주입된 TransferExecutionUseCase가 real 구현(TransferExecutionService)이다 - Mock이 여전히 @Primary를 못 이긴다는 전제 확인")
    void injectedBeanIsRealImplementation() {
        // getClass() 대신 AopUtils.getTargetClass()를 쓴다 - AOP 프록시로 감싸져도(나중에
        // @Transactional 등이 붙어도) 실제 타깃 클래스를 정확히 가리켜서 이 단정이 안 깨진다.
        assertThat(AopUtils.getTargetClass(transferExecutionUseCase).getSimpleName()).isEqualTo("TransferExecutionService");
    }

    @Test
    @DisplayName("real TransferExecutionUseCase로 completeProcessing()을 돌리면, " +
            "출금계좌 등록 플로우(#100) 부재로 채번 이전 단계에서 TRF0001로 안전하게 ERROR 확정된다 - " +
            "성공 케이스(원장 실제 기표) 자체는 #100 해결 전까지 여전히 재현 불가")
    void completeProcessing_withRealExecute_currentlyAlwaysFailsAtWithdrawalRegistration() {
        AutoTransferExecution saved = itemProcessor.saveProcessing(autoTransfer(), today);

        itemProcessor.completeProcessing(autoTransfer(), saved, today);

        var executionAfter = executionRepository.findById(saved.getExecutionId()).orElseThrow();
        assertThat(executionAfter.getStatus()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(executionAfter.getFailureReason()).isEqualTo("등록되지 않은 출금계좌입니다.");
        // 채번(TransferSequencePort.nextTransactionNumber) 이전 실패라 거래번호가 없고,
        // 그래서 감사로그도 안 남는다(AuditLogJpaEntity의 "원장 변경 이벤트는 transactionNumber 필수" 불변식과 일관됨)
        assertThat(executionAfter.getTransactionNumber()).isNull();
        assertThat(auditLogJpaRepository.findAll()).isEmpty();
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
