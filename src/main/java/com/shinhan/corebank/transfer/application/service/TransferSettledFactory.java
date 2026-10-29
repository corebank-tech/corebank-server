package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.transfer.api.TransferSettled;
import com.shinhan.corebank.transfer.domain.Transfer;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 확정된 이체 한 건으로 TransferSettled 를 만든다. 당행 확정 · 타행 송신 전 실패 · 타행 확정이 같은 모양의 이벤트와
 * 같은 고객 알림을 내도록 한 곳에 둔다. 발행 지점마다 다른 값은 수신 고객과 txType 뿐이다.
 * 알림 대상이 아닌 이체(예약 · 자동이체)는 비어 있다.
 */
final class TransferSettledFactory {

    static Optional<TransferSettled> create(
            Transfer transfer, Long customerId, String txType, LocalDateTime occurredAt) {
        return TransferNotificationGenerator.generate(transfer, customerId, occurredAt)
                .map(notification -> settled(transfer, customerId, txType, occurredAt, notification));
    }

    private static TransferSettled settled(
            Transfer transfer,
            Long customerId,
            String txType,
            LocalDateTime occurredAt,
            NotificationRequest notification) {
        return TransferSettled.builder()
                .customerId(customerId)
                .refId(transfer.getTransferId())
                .txType(txType)
                .status(transfer.getStatus())
                .errorCode(transfer.getErrorCode())
                .occurredAt(occurredAt)
                .notificationRequest(notification)
                .build();
    }
}
