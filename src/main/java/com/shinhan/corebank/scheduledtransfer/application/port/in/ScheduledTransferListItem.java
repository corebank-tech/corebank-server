package com.shinhan.corebank.scheduledtransfer.application.port.in;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;

import java.time.LocalDate;

public record ScheduledTransferListItem(Long scheduledTransferId,
                                        LocalDate scheduledDate,
                                        String withdrawalAccountNumber,
                                        String payeeBankCode,
                                        String payeeAccountNumber,
                                        String payeeName,
                                        Long amount,
                                        ScheduledTransferStatus status,
                                        boolean cancelable) {
}
