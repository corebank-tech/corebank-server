package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;

import java.time.LocalDateTime;

public record ScheduledTransferCancelResponse(Long scheduledTransferId, ScheduledTransferStatus status, LocalDateTime canceledAt) {
    public static ScheduledTransferCancelResponse from(ScheduledTransfer scheduledTransfer) {
        return new ScheduledTransferCancelResponse(
                scheduledTransfer.getScheduledTransferId(),
                scheduledTransfer.getStatus(),
                scheduledTransfer.getCanceledAt()
        );
    }
}
