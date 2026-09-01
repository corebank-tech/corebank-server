package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;

final class AutoTransferMapper {
    private AutoTransferMapper() {}

    static AutoTransfer toDomain(AutoTransferJpaEntity entity) {
        return AutoTransfer.reconstitute(
                entity.getAutoTransferId(),
                entity.getCustomerId(),
                entity.getWithdrawalAccountId(),
                entity.getDepositAccountNumber(),
                entity.getPayeeName(),
                entity.getAmount(),
                entity.getCycleMonths(),
                entity.getTransferDay(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getNextExecutionDate(),
                entity.getMyPassbookMemo(),
                entity.getRecipientPassbookMemo(),
                entity.getStatus(),
                entity.getRegisteredAt(),
                entity.getTerminatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion());
    }

    static AutoTransferJpaEntity toEntity(AutoTransfer domain) {
        return AutoTransferJpaEntity.builder()
                .autoTransferId(domain.getAutoTransferId())
                .customerId(domain.getCustomerId())
                .withdrawalAccountId(domain.getWithdrawalAccountId())
                .depositAccountNumber(domain.getDepositAccountNumber())
                .payeeName(domain.getPayeeName())
                .amount(domain.getAmount())
                .cycleMonths(domain.getCycleMonths())
                .transferDay(domain.getTransferDay())
                .startDate(domain.getStartDate())
                .endDate(domain.getEndDate())
                .nextExecutionDate(domain.getNextExecutionDate())
                .myPassbookMemo(domain.getMyPassbookMemo())
                .recipientPassbookMemo(domain.getRecipientPassbookMemo())
                .status(domain.getStatus())
                .registeredAt(domain.getRegisteredAt())
                .terminatedAt(domain.getTerminatedAt())
                .updatedAt(domain.getUpdatedAt())
                .version(domain.getVersion())
                .build();
    }
}
