package com.shinhan.corebank.scheduledtransfer.application.port.in;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;

import java.time.LocalDateTime;

// executedAt은 실제 실행(확정)시각(api_conventions.md §6-4) - CANCELED 건은 실행된 적이 없어 null
public record ScheduledTransferExecutionResultItem(Long scheduledTransferId,
                                                    ScheduledTransferStatus status,
                                                    LocalDateTime executedAt,
                                                    String withdrawalAccountNumber,
                                                    String payeeAccountNumber,
                                                    String payeeName,
                                                    Long amount,
                                                    String transactionNumber,
                                                    String failureReason) {
}
