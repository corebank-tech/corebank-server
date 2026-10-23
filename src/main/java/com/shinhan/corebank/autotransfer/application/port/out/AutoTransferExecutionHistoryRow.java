package com.shinhan.corebank.autotransfer.application.port.out;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AutoTransferExecutionHistoryRow(
        Long executionId,
        Long autoTransferId,
        LocalDate executionDate,
        ProcessResultStatus status,
        LocalDateTime executedAt,
        Long withdrawalAccountId,
        String depositAccountNumber,
        String payeeName,
        Long amount,
        Integer cycleMonths,
        String myPassbookMemo,
        String failureReason) {}
