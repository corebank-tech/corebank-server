package com.shinhan.corebank.transfer.application.port.in;

import java.time.LocalDateTime;

import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.TransferChannel;

import lombok.Builder;

// transactionTypeName은 담지 않는다 — P2가 공통코드로 매핑하기로 확정됐다(P4 회신 5절).
@Builder
public record LedgerHistoryItem(
        Long ledgerEntryId,
        String transactionNumber,
        LocalDateTime occurredAt,
        String transactionType,
        LedgerDirection direction,
        long amount,
        String transactionContent,
        long balanceAfter,
        TransferChannel channel
) {
}
