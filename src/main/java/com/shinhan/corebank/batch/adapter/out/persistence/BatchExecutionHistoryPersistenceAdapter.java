package com.shinhan.corebank.batch.adapter.out.persistence;

import com.shinhan.corebank.batch.api.BatchExecutionHistoryPort;
import com.shinhan.corebank.batch.api.BatchStepStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BatchExecutionHistoryPersistenceAdapter implements BatchExecutionHistoryPort {

    private final BatchExecutionHistoryJpaRepository batchExecutionHistoryJpaRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String jobName,
            String stepName,
            LocalDate businessDate,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            BatchStepStatus status,
            String failureReason) {
        batchExecutionHistoryJpaRepository.save(BatchExecutionHistoryJpaEntity.of(
                jobName, stepName, businessDate, startedAt, endedAt, status, failureReason));
    }
}
