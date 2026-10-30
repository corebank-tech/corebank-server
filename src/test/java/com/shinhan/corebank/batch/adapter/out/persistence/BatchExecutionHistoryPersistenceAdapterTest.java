package com.shinhan.corebank.batch.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.batch.api.BatchStepStatus;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// record()가 REQUIRES_NEW로 실제 커밋되는지 봐야 하므로 클래스 레벨 @Transactional을 쓰지 않는다
// (BatchExecutionLockPersistenceAdapterTest와 동일한 이유).
class BatchExecutionHistoryPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    BatchExecutionHistoryJpaRepository batchExecutionHistoryJpaRepository;

    @Autowired
    BatchExecutionHistoryPersistenceAdapter adapter;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    Clock clock;

    private static final AtomicLong JOB_SEQ = new AtomicLong();

    private String jobName;

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        jobName = "TEST_JOB_" + JOB_SEQ.incrementAndGet();
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate().executeWithoutResult(status -> entityManager
                .createNativeQuery("DELETE FROM batch_execution_history WHERE job_name = :jobName")
                .setParameter("jobName", jobName)
                .executeUpdate());
    }

    @Test
    @DisplayName("성공한 스텝은 SUCCESS 상태로, failureReason 없이 저장된다")
    void record_success_persistsWithoutFailureReason() {
        LocalDate businessDate = LocalDate.of(2026, 10, 12);
        LocalDateTime startedAt = LocalDateTime.now(clock).minusMinutes(1);
        LocalDateTime endedAt = LocalDateTime.now(clock);

        adapter.record(
                jobName, "ledger-reconciliation", businessDate, startedAt, endedAt, BatchStepStatus.SUCCESS, null);

        List<BatchExecutionHistoryJpaEntity> persisted = batchExecutionHistoryJpaRepository.findAll().stream()
                .filter(e -> e.getJobName().equals(jobName))
                .toList();
        assertThat(persisted).hasSize(1);
        BatchExecutionHistoryJpaEntity entity = persisted.get(0);
        assertThat(entity.getStepName()).isEqualTo("ledger-reconciliation");
        assertThat(entity.getBusinessDate()).isEqualTo(businessDate);
        assertThat(entity.getStatus()).isEqualTo(BatchStepStatus.SUCCESS.name());
        assertThat(entity.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("중단(hard)된 스텝은 HALTED 상태와 failureReason을 함께 저장한다")
    void record_halted_persistsWithFailureReason() {
        LocalDate businessDate = LocalDate.of(2026, 10, 12);
        LocalDateTime startedAt = LocalDateTime.now(clock).minusMinutes(1);
        LocalDateTime endedAt = LocalDateTime.now(clock);

        adapter.record(
                jobName,
                "ledger-reconciliation",
                businessDate,
                startedAt,
                endedAt,
                BatchStepStatus.HALTED,
                "불일치 임계 초과");

        BatchExecutionHistoryJpaEntity entity = batchExecutionHistoryJpaRepository.findAll().stream()
                .filter(e -> e.getJobName().equals(jobName))
                .findFirst()
                .orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(BatchStepStatus.HALTED.name());
        assertThat(entity.getFailureReason()).isEqualTo("불일치 임계 초과");
    }
}
