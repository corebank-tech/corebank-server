package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferListItem;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AutoTransferListItemResponse(Long autoTransferId, String depositAccountNumber, String fromAlias,
                                           String payeeName, Long amount, LocalDate startDate, LocalDate endDate,
                                           Integer transferDay, Integer cycleMonths, String myPassbookMemo,
                                           AutoTransferStatus status, LocalDateTime registeredAt) {
    public static AutoTransferListItemResponse from(AutoTransferListItem item) {
        AutoTransfer autoTransfer = item.autoTransfer();
        return new AutoTransferListItemResponse(autoTransfer.getAutoTransferId(),
                autoTransfer.getDepositAccountNumber(), item.fromAlias(), autoTransfer.getPayeeName(), autoTransfer.getAmount(),
                autoTransfer.getStartDate(), autoTransfer.getEndDate(), autoTransfer.getTransferDay(), autoTransfer.getCycleMonths(),
                autoTransfer.getMyPassbookMemo(), autoTransfer.getStatus(), autoTransfer.getRegisteredAt());
    }
}
