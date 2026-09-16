package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferExecutionHistoryItem;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

// 실행 이력 한 줄을 API 응답으로 감싼 DTO
public record AutoTransferExecutionHistoryItemResponse(
        @Schema(description = "실행 이력 ID") Long executionId,
        @Schema(description = "실행 결과. SUCCESS/ERROR") ProcessResultStatus status,
        @Schema(description = "실행 시각") LocalDateTime executedAt,
        @Schema(description = "출금계좌 ID (내 계좌)") Long withdrawalAccountId,
        @Schema(description = "입금계좌번호") String depositAccountNumber,
        @Schema(description = "예금주명") String payeeName,
        @Schema(description = "이체금액") Long amount,
        @Schema(description = "이체주기(개월)") Integer cycleMonths,
        @Schema(description = "내 통장 표시내용") String myPassbookMemo,
        @Schema(description = "실패 사유. status=SUCCESS일 때는 비어있음", nullable = true) String failureReason) {
    public static AutoTransferExecutionHistoryItemResponse from(AutoTransferExecutionHistoryItem item) {
        return new AutoTransferExecutionHistoryItemResponse(
                item.executionId(),
                item.status(),
                item.executedAt(),
                item.withdrawalAccountId(),
                item.depositAccountNumber(),
                item.payeeName(),
                item.amount(),
                item.cycleMonths(),
                item.myPassbookMemo(),
                item.failureReason());
    }
}
