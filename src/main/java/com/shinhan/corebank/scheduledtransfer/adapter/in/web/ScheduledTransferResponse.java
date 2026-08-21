package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record ScheduledTransferResponse(
        @Schema(description = "예약이체 ID")
        Long scheduledTransferId,
        @Schema(description = "출금계좌 ID (내 계좌)")
        Long withdrawalAccountId,
        @Schema(description = "입금계좌번호")
        String depositAccountNumber,
        @Schema(description = "예금주명")
        String payeeName,
        @Schema(description = "이체금액")
        Long amount,
        @Schema(description = "예약 실행일")
        LocalDate scheduledDate,
        @Schema(description = "내 통장 표시내용")
        String myPassbookMemo,
        @Schema(description = "상대 통장 표시내용")
        String recipientPassbookMemo,
        @Schema(description = "예약이체 상태")
        ScheduledTransferStatus status) {
    public static ScheduledTransferResponse from(ScheduledTransfer scheduledTransfer) {
        return new ScheduledTransferResponse(
                scheduledTransfer.getScheduledTransferId(),
                scheduledTransfer.getWithdrawalAccountId(),
                scheduledTransfer.getPayeeAccountNumber(),
                scheduledTransfer.getPayeeName(),
                scheduledTransfer.getAmount(),
                scheduledTransfer.getScheduledDate(),
                scheduledTransfer.getMyPassbookMemo(),
                scheduledTransfer.getRecipientPassbookMemo(),
                scheduledTransfer.getStatus()
        );
    }
}
