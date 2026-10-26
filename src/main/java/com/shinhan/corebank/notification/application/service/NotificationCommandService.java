package com.shinhan.corebank.notification.application.service;

import com.shinhan.corebank.notification.api.NotificationType;
import com.shinhan.corebank.notification.application.port.in.NotificationCommandUseCase;
import com.shinhan.corebank.notification.application.port.in.dto.NotificationTransferResultCommand;
import com.shinhan.corebank.notification.application.port.out.NotificationCommandPort;
import com.shinhan.corebank.notification.domain.Notification;
import com.shinhan.corebank.notification.domain.NotificationTransferMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationCommandService implements NotificationCommandUseCase {

    private final NotificationCommandPort notificationCommandPort;

    @Override
    public void createTransferResultNotification(NotificationTransferResultCommand command) {
        NotificationTransferMessage message = command.isSucceeded()
                ? NotificationTransferMessage.succeeded(command.amount(), command.counterpartyName())
                : NotificationTransferMessage.failed(command.amount(), command.counterpartyName(), command.errorCode());

        // 발생 시각은 이벤트 값을 쓴다 — 재제출로 늦게 처리돼도 알림함에는 이체가 확정된 시각이 남는다
        notificationCommandPort.saveIfAbsent(Notification.builder()
                .customerId(command.customerId())
                .type(NotificationType.TRANSFER)
                .refId(command.transferId())
                .title(message.title())
                .content(message.content())
                .occurredAt(command.occurredAt())
                .build());
    }
}
