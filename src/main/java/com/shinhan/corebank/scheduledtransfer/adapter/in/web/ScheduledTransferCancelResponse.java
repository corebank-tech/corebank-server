package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ScheduledTransferCancelResponse(
        @Schema(description = "예약이체 ID")
        Long scheduledTransferId,
        @Schema(description = "예약이체 상태")
        ScheduledTransferStatus status,
        @Schema(description = "취소 시각")
        LocalDateTime canceledAt) {
    public static ScheduledTransferCancelResponse from(ScheduledTransfer scheduledTransfer) {
        return new ScheduledTransferCancelResponse(
                scheduledTransfer.getScheduledTransferId(),
                scheduledTransfer.getStatus(),
                scheduledTransfer.getCanceledAt()
        );
    }
}
