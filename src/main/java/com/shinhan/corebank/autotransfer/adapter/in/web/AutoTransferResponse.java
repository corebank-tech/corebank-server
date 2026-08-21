package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record AutoTransferResponse (
        @Schema(description = "자동이체 ID")
        Long autoTransferId,
        @Schema(description = "출금계좌 ID (내 계좌)")
        Long withdrawalAccountId,
        @Schema(description = "입금계좌번호")
        String depositAccountNumber,
        @Schema(description = "예금주명")
        String payeeName,
        @Schema(description = "회당 이체금액")
        Long amount,
        @Schema(description = "이체주기(개월)")
        Integer cycleMonths,
        @Schema(description = "이체지정일")
        Integer transferDay,
        @Schema(description = "이체 시작일")
        LocalDate startDate,
        @Schema(description = "이체 종료일")
        LocalDate endDate,
        @Schema(description = "다음 이체 예정일")
        LocalDate nextExecutionDate,
        @Schema(description = "내 통장 표시내용")
        String myPassbookMemo,
        @Schema(description = "상대 통장 표시내용")
        String recipientPassbookMemo,
        @Schema(description = "자동이체 상태")
        AutoTransferStatus status) {
    public static AutoTransferResponse from(AutoTransfer autoTransfer) {
        return new AutoTransferResponse(
                autoTransfer.getAutoTransferId(),
                autoTransfer.getWithdrawalAccountId(),
                autoTransfer.getDepositAccountNumber(),
                autoTransfer.getPayeeName(),
                autoTransfer.getAmount(),
                autoTransfer.getCycleMonths(),
                autoTransfer.getTransferDay(),
                autoTransfer.getStartDate(),
                autoTransfer.getEndDate(),
                autoTransfer.getNextExecutionDate(),
                autoTransfer.getMyPassbookMemo(),
                autoTransfer.getRecipientPassbookMemo(),
                autoTransfer.getStatus()
        );
    }
}
