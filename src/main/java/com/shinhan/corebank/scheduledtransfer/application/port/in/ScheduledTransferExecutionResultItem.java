package com.shinhan.corebank.scheduledtransfer.application.port.in;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;

import java.time.LocalDate;

public record ScheduledTransferExecutionResultItem(Long scheduledTransferId,
                                                    ScheduledTransferStatus status,
                                                    LocalDate scheduledDate,
                                                    String withdrawalAccountNumber,
                                                    String payeeAccountNumber,
                                                    String payeeName,
                                                    Long amount,
                                                    String transactionNumber,
                                                    String failureReason) {
}
