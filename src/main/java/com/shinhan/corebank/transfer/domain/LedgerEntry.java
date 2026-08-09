package com.shinhan.corebank.transfer.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class LedgerEntry {

    private final Long ledgerEntryId;
    private final Long accountId;
    private final Long transferId;
    private final String transactionNumber;
    private final LedgerDirection direction;
    private final long amount; // 양수 허용
    private final long balanceAfter;
    private final String transactionType;
    private final String transactionContent;
    private final TransferChannel channel;
    private final boolean reversed;
    private final Long reversalId;
    private final LocalDateTime occurredAt;



}
