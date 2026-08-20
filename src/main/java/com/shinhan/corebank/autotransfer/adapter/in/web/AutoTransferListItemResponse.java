package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferListItem;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AutoTransferListItemResponse(Long autoTransferId, String depositAccountNumber, String fromAlias,
                                           String payeeName, Long amount, LocalDate startDate, LocalDate endDate,
                                           Integer transferDay, Integer cycleMonths, String myPassbookMemo,
                                           AutoTransferStatus status, LocalDateTime registeredAt) {
    public static AutoTransferListItemResponse from(AutoTransferListItem item) {
        return new AutoTransferListItemResponse(item.autoTransferId(), item.depositAccountNumber(), item.fromAlias(),
                item.payeeName(), item.amount(), item.startDate(), item.endDate(), item.transferDay(), item.cycleMonths(),
                item.myPassbookMemo(), item.status(), item.registeredAt());
    }
}
