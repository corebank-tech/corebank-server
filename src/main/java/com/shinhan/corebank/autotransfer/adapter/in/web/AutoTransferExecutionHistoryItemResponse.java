package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferExecutionHistoryItem;
import com.shinhan.corebank.common.domain.ProcessResultStatus;

import java.time.LocalDateTime;

// 실행 이력 한 줄을 API 응답으로 감싼 DTO
public record AutoTransferExecutionHistoryItemResponse(Long executionId,
                                                       ProcessResultStatus status,
                                                       LocalDateTime executedAt,
                                                       Long withdrawalAccountId,
                                                       String depositAccountNumber,
                                                       String payeeName,
                                                       Long amount,
                                                       Integer cycleMonths,
                                                       String myPassbookMemo,
                                                       String failureReason) {
    public static AutoTransferExecutionHistoryItemResponse from(AutoTransferExecutionHistoryItem item) {
        return new AutoTransferExecutionHistoryItemResponse(item.executionId(), item.status(), item.executedAt(),
                item.withdrawalAccountId(), item.depositAccountNumber(), item.payeeName(), item.amount(),
                item.cycleMonths(), item.myPassbookMemo(), item.failureReason());
    }
}
