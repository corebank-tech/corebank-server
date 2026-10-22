package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferExecutionHistoryItem;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
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
        @Schema(
                        description = "거래일. 이 회차가 만든 이체가 귀속되는 영업일이라 휴일에 실행되면 executedAt 날짜의 다음 영업일이다. "
                                + "이체를 만들기 전에 실패한 ERROR와 이력 없이 합성된 행(실패 사유 '관리자에게 문의해주세요')은 비어 있다",
                        nullable = true)
                LocalDate tradeDate,
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
                item.tradeDate(),
                item.failureReason());
    }
}
