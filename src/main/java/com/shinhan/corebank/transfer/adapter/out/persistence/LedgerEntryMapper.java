package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.temporal.ChronoUnit;

import com.shinhan.corebank.transfer.domain.LedgerEntry;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerEntryMapper {

    public static LedgerEntryJpaEntity toEntity(LedgerEntry domain) {
        if (domain == null) {
            return null;
        }

        return LedgerEntryJpaEntity.builder()
                .ledgerEntryId(domain.getLedgerEntryId())
                .accountId(domain.getAccountId())
                .transferId(domain.getTransferId())
                .transactionNumber(domain.getTransactionNumber())
                .direction(domain.getDirection())
                .amount(domain.getAmount())
                .balanceAfter(domain.getBalanceAfter())
                .transactionType(domain.getTransactionType())
                .transactionContent(domain.getTransactionContent())
                .channel(domain.getChannel())
                .reversed(domain.isReversed())
                .reversalId(domain.getReversalId())
                .occurredAt(domain.getOccurredAt() != null ? domain.getOccurredAt().truncatedTo(ChronoUnit.MICROS) : null)
                .build();
    }

    public static LedgerEntry toDomain(LedgerEntryJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        return LedgerEntry.builder()
                .ledgerEntryId(entity.getLedgerEntryId())
                .accountId(entity.getAccountId())
                .transferId(entity.getTransferId())
                .transactionNumber(entity.getTransactionNumber())
                .direction(entity.getDirection())
                .amount(entity.getAmount())
                .balanceAfter(entity.getBalanceAfter())
                .transactionType(entity.getTransactionType())
                .transactionContent(entity.getTransactionContent())
                .channel(entity.getChannel())
                .reversed(entity.isReversed())
                .reversalId(entity.getReversalId())
                .occurredAt(entity.getOccurredAt() != null ? entity.getOccurredAt().truncatedTo(ChronoUnit.MICROS) : null)
                .build();
    }
}
