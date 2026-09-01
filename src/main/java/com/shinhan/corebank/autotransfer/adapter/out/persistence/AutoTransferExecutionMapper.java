package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;

final class AutoTransferExecutionMapper {
    private AutoTransferExecutionMapper() {}

    static AutoTransferExecution toDomain(AutoTransferExecutionJpaEntity entity) {
        return AutoTransferExecution.reconstitute(
                entity.getExecutionId(),
                entity.getExecutionDate(),
                entity.getAmount(),
                entity.getStatus(),
                entity.getTransactionNumber(),
                entity.getFailureReason(),
                entity.getExecutedAt());
    }

    static AutoTransferExecutionJpaEntity toEntity(AutoTransferExecution domain, AutoTransferJpaEntity autoTransfer) {
        return AutoTransferExecutionJpaEntity.builder()
                .executionId(domain.getExecutionId())
                .autoTransfer(autoTransfer)
                .executionDate(domain.getExecutionDate())
                .amount(domain.getAmount())
                .status(domain.getStatus())
                .transactionNumber(domain.getTransactionNumber())
                .failureReason(domain.getFailureReason())
                .executedAt(domain.getExecutedAt())
                .build();
    }
}
