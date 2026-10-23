package com.shinhan.corebank.notification.adapter.in.event;

import com.shinhan.corebank.autotransfer.api.AutoTransferExecutionSettled;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.notification.application.port.in.NotificationCommandUseCase;
import com.shinhan.corebank.notification.application.port.in.dto.NotificationTransferResultCommand;
import com.shinhan.corebank.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 자동이체 회차 결과를 알림으로 남긴다(#707). 배치가 회차마다 결과를 확정하면서 발행하는 이벤트라 refId 가
 * 등록 ID 가 아니라 회차의 execution_id 이고, 알림 ref_id 규약과 그대로 맞는다.
 */
@Component
@RequiredArgsConstructor
public class NotificationAutoTransferExecutionSettledListener {

    private final NotificationCommandUseCase notificationCommandUseCase;

    @ApplicationModuleListener
    public void on(AutoTransferExecutionSettled event) {
        notificationCommandUseCase.createTransferResultNotification(NotificationTransferResultCommand.builder()
                .customerId(event.customerId())
                .type(NotificationType.AUTO_TRANSFER)
                .refId(event.refId())
                .isSucceeded(event.status() == ProcessResultStatus.SUCCESS)
                .errorCode(event.errorCode())
                .amount(event.amount())
                .counterpartyName(event.counterpartyName())
                .occurredAt(event.occurredAt())
                .build());
    }
}
