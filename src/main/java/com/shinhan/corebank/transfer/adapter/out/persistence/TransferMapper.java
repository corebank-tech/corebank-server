package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.domain.Transfer;
import lombok.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TransferMapper {
    public static TransferJpaEntity toEntity(Transfer domain) {
        if (domain == null) {
            return null;
        }

        return TransferJpaEntity.builder()
                .transferId(domain.getTransferId())
                .transactionNumber(domain.getTransactionNumber())
                .withdrawalAccountId(domain.getWithdrawalAccountId())
                .depositAccountId(domain.getDepositAccountId())
                .depositAccountNumber(domain.getDepositAccountNumber())
                .payeeName(domain.getPayeeName())
                .amount(domain.getAmount())
                .fee(domain.getFee())
                .transferType(domain.getTransferType())
                .channel(domain.getChannel())
                .status(domain.getStatus())
                .sourceType(domain.getSourceType())
                .sourceId(domain.getSourceId())
                .executionDate(domain.getExecutionDate())
                .myPassbookMemo(domain.getMyPassbookMemo())
                .recipientPassbookMemo(domain.getRecipientPassbookMemo())
                .withdrawalBalanceAfter(domain.getWithdrawalBalanceAfter())
                .errorCode(domain.getErrorCode())
                .errorMessage(domain.getErrorMessage())
                .transferredAt(domain.getTransferredAt())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public static Transfer toDomain(TransferJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        return Transfer.builder()
                .transferId(entity.getTransferId())
                .transactionNumber(entity.getTransactionNumber())
                .withdrawalAccountId(entity.getWithdrawalAccountId())
                .depositAccountId(entity.getDepositAccountId())
                .depositAccountNumber(entity.getDepositAccountNumber())
                .payeeName(entity.getPayeeName())
                .amount(entity.getAmount())
                .fee(entity.getFee())
                .transferType(entity.getTransferType())
                .channel(entity.getChannel())
                .status(entity.getStatus())
                .sourceType(entity.getSourceType())
                .sourceId(entity.getSourceId())
                .executionDate(entity.getExecutionDate())
                .myPassbookMemo(entity.getMyPassbookMemo())
                .recipientPassbookMemo(entity.getRecipientPassbookMemo())
                .withdrawalBalanceAfter(entity.getWithdrawalBalanceAfter())
                .errorCode(entity.getErrorCode())
                .errorMessage(entity.getErrorMessage())
                .transferredAt(entity.getTransferredAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
