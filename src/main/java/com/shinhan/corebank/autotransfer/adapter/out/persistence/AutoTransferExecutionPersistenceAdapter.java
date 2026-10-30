package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferExecutionPersistencePort;
import com.shinhan.corebank.autotransfer.application.port.out.StuckExecution;
import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AutoTransferExecutionPersistenceAdapter implements AutoTransferExecutionPersistencePort {
    private final AutoTransferExecutionJpaRepository autoTransferExecutionJpaRepository;
    private final AutoTransferJpaRepository autoTransferJpaRepository;

    @Override
    public AutoTransferExecution save(AutoTransferExecution execution, Long autoTransferId) {
        AutoTransferJpaEntity autoTransfer = autoTransferJpaRepository.getReferenceById(autoTransferId);
        AutoTransferExecutionJpaEntity entity = AutoTransferExecutionMapper.toEntity(execution, autoTransfer);
        AutoTransferExecutionJpaEntity saved = autoTransferExecutionJpaRepository.saveAndFlush(entity);
        return AutoTransferExecutionMapper.toDomain(saved);
    }

    @Override
    public List<StuckExecution> findAllProcessing() {
        return autoTransferExecutionJpaRepository
                .findAllByStatusWithAutoTransfer(ProcessResultStatus.PROCESSING)
                .stream()
                .map(entity -> new StuckExecution(
                        AutoTransferMapper.toDomain(entity.getAutoTransfer()),
                        AutoTransferExecutionMapper.toDomain(entity)))
                .toList();
    }

    @Override
    public Optional<AutoTransferExecution> findProcessingForUpdate(Long executionId) {
        return autoTransferExecutionJpaRepository
                .findForUpdateByExecutionIdAndStatus(executionId, ProcessResultStatus.PROCESSING)
                .map(AutoTransferExecutionMapper::toDomain);
    }

    @Override
    public Optional<AutoTransferExecution> findProcessingForUpdateSkipLocked(Long executionId) {
        return autoTransferExecutionJpaRepository
                .findForUpdateSkipLockedByExecutionIdAndStatus(executionId, ProcessResultStatus.PROCESSING)
                .map(AutoTransferExecutionMapper::toDomain);
    }

    @Override
    public List<AutoTransferExecution> findRecentByAutoTransferId(Long autoTransferId, int limit) {
        return autoTransferExecutionJpaRepository
                .findRecentByAutoTransferId(autoTransferId, PageRequest.of(0, limit))
                .stream()
                .map(AutoTransferExecutionMapper::toDomain)
                .toList();
    }
}
