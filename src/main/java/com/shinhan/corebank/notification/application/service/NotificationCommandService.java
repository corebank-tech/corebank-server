package com.shinhan.corebank.notification.application.service;

import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.application.port.in.NotificationCommandUseCase;
import com.shinhan.corebank.notification.application.port.out.NotificationCommandPort;
import com.shinhan.corebank.notification.domain.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@Transactional
public class NotificationCommandService implements NotificationCommandUseCase {

    private final NotificationCommandPort notificationCommandPort;

    @Override
    public void createNotification(NotificationRequest request) {
        // 발생 시각은 업무가 확정된 시각이다 — 재제출로 늦게 처리돼도 알림함에는 그 시각이 남는다
        notificationCommandPort.saveIfAbsent(Notification.builder()
                .customerId(request.customerId())
                .type(request.type())
                .refId(request.refId())
                .title(request.title())
                .content(request.content())
                .occurredAt(request.occurredAt())
                .build());
    }
}
