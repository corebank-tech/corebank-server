package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;

import java.time.LocalDate;

public record ScheduledTransferResponse(Long scheduledTransferId, Long withdrawalAccountId, String depositAccountNumber,
                                        String payeeName, Long amount, LocalDate scheduledDate,
                                        String myPassbookMemo, String recipientPassbookMemo,
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
