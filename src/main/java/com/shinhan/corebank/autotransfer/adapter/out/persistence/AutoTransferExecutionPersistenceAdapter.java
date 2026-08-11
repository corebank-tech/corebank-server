package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferExecutionPersistencePort;
import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;
import lombok.RequiredArgsConstructor;
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
}
