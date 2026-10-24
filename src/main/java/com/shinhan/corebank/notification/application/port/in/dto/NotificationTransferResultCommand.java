package com.shinhan.corebank.notification.application.port.in.dto;

import java.time.LocalDateTime;
import lombok.Builder;

/** 확정된 이체 한 건의 결과. */
@Builder
public record NotificationTransferResultCommand(
        Long customerId,
        Long transferId,
        boolean isSucceeded,
        String errorCode,
        long amount,
        String counterpartyName,
        LocalDateTime occurredAt) {}
