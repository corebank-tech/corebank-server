package com.shinhan.corebank.autotransfer.application.port.in;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import java.time.LocalDateTime;

// 자동이체 실행 이력 목록에 보여줄 한 줄
public record AutoTransferExecutionHistoryItem(
        Long executionId,
        ProcessResultStatus status,
        LocalDateTime executedAt,
        Long withdrawalAccountId,
        String depositAccountNumber,
        String payeeName,
        Long amount,
        Integer cycleMonths,
        String myPassbookMemo,
        String failureReason) {}
