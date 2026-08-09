package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;

import java.time.LocalDate;

public record AutoTransferListItemResponse(Long autoTransferId, String depositAccountNumber, String payeeName,
                                           Long amount, LocalDate startDate, LocalDate endDate, Integer transferDay,
                                           Integer cycleMonths, String myPassbookMemo, AutoTransferStatus status) {
    public static AutoTransferListItemResponse from(AutoTransfer autoTransfer) {
        return new AutoTransferListItemResponse(autoTransfer.getAutoTransferId(),
                autoTransfer.getDepositAccountNumber(), autoTransfer.getPayeeName(), autoTransfer.getAmount(),
                autoTransfer.getStartDate(), autoTransfer.getEndDate(), autoTransfer.getTransferDay(), autoTransfer.getCycleMonths(),
                autoTransfer.getMyPassbookMemo(), autoTransfer.getStatus());
    }
}
