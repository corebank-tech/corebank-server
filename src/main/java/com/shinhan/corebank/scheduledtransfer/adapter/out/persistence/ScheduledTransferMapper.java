package com.shinhan.corebank.scheduledtransfer.adapter.out.persistence;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;

final class ScheduledTransferMapper {
    private ScheduledTransferMapper() {}

    static ScheduledTransfer toDomain(ScheduledTransferJpaEntity entity) {
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

    static ScheduledTransferJpaEntity toEntity(ScheduledTransfer domain) {
        return ScheduledTransferJpaEntity.builder()
                .scheduledTransferId(domain.getScheduledTransferId())
                .customerId(domain.getCustomerId())
                .withdrawalAccountId(domain.getWithdrawalAccountId())
                .payeeBankCode(domain.getPayeeBankCode())
                .payeeAccountNumber(domain.getPayeeAccountNumber())
                .payeeName(domain.getPayeeName())
                .amount(domain.getAmount())
                .scheduledDate(domain.getScheduledDate())
                .myPassbookMemo(domain.getMyPassbookMemo())
                .recipientPassbookMemo(domain.getRecipientPassbookMemo())
                .status(domain.getStatus())
                .transactionNumber(domain.getTransactionNumber())
                .registeredAt(domain.getRegisteredAt())
                .executedAt(domain.getExecutedAt())
                .canceledAt(domain.getCanceledAt())
                .failureReason(domain.getFailureReason())
                .version(domain.getVersion())
                .build();
    }
}
