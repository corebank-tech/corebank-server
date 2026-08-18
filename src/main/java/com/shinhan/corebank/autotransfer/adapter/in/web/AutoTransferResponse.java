package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;

import java.time.LocalDate;

public record AutoTransferResponse (Long autoTransferId, Long withdrawalAccountId, String depositAccountNumber,
                                    String payeeName, Long amount, Integer cycleMonths, Integer transferDay,
                                    LocalDate startDate, LocalDate endDate, LocalDate nextExecutionDate, String myPassbookMemo,
                                    String recipientPassbookMemo, AutoTransferStatus status) {
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
