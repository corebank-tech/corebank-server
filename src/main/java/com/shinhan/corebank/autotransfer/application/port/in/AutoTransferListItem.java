package com.shinhan.corebank.autotransfer.application.port.in;

import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AutoTransferListItem(Long autoTransferId,
                                   String depositAccountNumber,
                                   String fromAlias,
                                   String payeeName,
                                   Long amount,
                                   LocalDate startDate,
                                   LocalDate endDate,
                                   Integer transferDay,
                                   Integer cycleMonths,
                                   String myPassbookMemo,
                                   AutoTransferStatus status,
                                   LocalDateTime registeredAt) {
}
