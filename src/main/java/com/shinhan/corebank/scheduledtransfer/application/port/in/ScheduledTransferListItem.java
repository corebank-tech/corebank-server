package com.shinhan.corebank.scheduledtransfer.application.port.in;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ScheduledTransferListItem(
        Long scheduledTransferId,
        Long withdrawalAccountId,
        LocalDate scheduledDate,
        String withdrawalAccountNumber,
        String fromAlias,
        String payeeBankCode,
        String payeeAccountNumber,
        String payeeName,
        Long amount,
        String myPassbookMemo,
        ScheduledTransferStatus status,
        boolean cancelable,
        LocalDateTime registeredAt) {}
