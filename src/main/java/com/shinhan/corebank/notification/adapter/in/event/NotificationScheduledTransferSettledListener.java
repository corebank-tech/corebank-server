package com.shinhan.corebank.notification.adapter.in.event;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.notification.application.port.in.NotificationCommandUseCase;
import com.shinhan.corebank.notification.application.port.in.dto.NotificationTransferResultCommand;
import com.shinhan.corebank.notification.domain.NotificationType;
import com.shinhan.corebank.scheduledtransfer.api.ScheduledTransferSettled;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 예약이체 결과를 알림으로 남긴다(#707). 배치가 예약 건마다 결과를 확정하면서 발행하는 이벤트라 refId 가
 * scheduled_transfer_id 이고, 알림 ref_id 규약과 그대로 맞는다.
 */
@Component
@RequiredArgsConstructor
public class NotificationScheduledTransferSettledListener {

    private final NotificationCommandUseCase notificationCommandUseCase;

    @ApplicationModuleListener
    public void on(ScheduledTransferSettled event) {
        notificationCommandUseCase.createTransferResultNotification(NotificationTransferResultCommand.builder()
                .customerId(event.customerId())
                .type(NotificationType.SCHEDULED_TRANSFER)
                .refId(event.refId())
                .isSucceeded(event.status() == ProcessResultStatus.SUCCESS)
                .errorCode(event.errorCode())
                .amount(event.amount())
                .counterpartyName(event.counterpartyName())
                .occurredAt(event.occurredAt())
                .build());
    }
}
