package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.transfer.api.TransferSettled;
import com.shinhan.corebank.transfer.domain.Transfer;
import java.time.LocalDateTime;

/**
 * 확정된 이체 한 건으로 TransferSettled 를 만든다. 당행 확정 · 타행 송신 전 실패 · 타행 확정이 같은 모양의 이벤트와
 * 같은 고객 알림을 내도록 한 곳에 둔다. 발행 지점마다 다른 값은 수신 고객과 txType 뿐이다.
 */
final class TransferSettledFactory {

    static TransferSettled create(Transfer transfer, Long customerId, String txType, LocalDateTime occurredAt) {
        return TransferSettled.builder()
                .customerId(customerId)
                .refId(transfer.getTransferId())
                .txType(txType)
                .status(transfer.getStatus())
                .errorCode(transfer.getErrorCode())
                .occurredAt(occurredAt)
                .notificationRequest(TransferNotificationGenerator.generate(transfer, customerId, occurredAt))
                .build();
    }
}
