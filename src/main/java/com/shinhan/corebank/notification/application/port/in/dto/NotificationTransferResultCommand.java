package com.shinhan.corebank.notification.application.port.in.dto;

import com.shinhan.corebank.notification.domain.NotificationType;
import java.time.LocalDateTime;
import lombok.Builder;

/** 확정된 이체 한 건의 결과. refId 는 type 이 정하는 업무 ID 다(notification.ref_id 규약). */
@Builder
public record NotificationTransferResultCommand(
        Long customerId,
        NotificationType type,
        Long refId,
        boolean isSucceeded,
        String errorCode,
        long amount,
        String counterpartyName,
        LocalDateTime occurredAt) {}
