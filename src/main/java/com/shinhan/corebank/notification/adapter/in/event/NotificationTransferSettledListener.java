package com.shinhan.corebank.notification.adapter.in.event;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.notification.application.port.in.NotificationCommandUseCase;
import com.shinhan.corebank.notification.application.port.in.dto.NotificationTransferResultCommand;
import com.shinhan.corebank.notification.domain.NotificationType;
import com.shinhan.corebank.transfer.api.TransferSettled;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 즉시 · 타행이체 결과를 알림으로 남긴다(#706). 이체가 커밋된 뒤 별도 스레드 · 새 트랜잭션에서 돌고, 실패하면
 * 발행 기록(EVENT_PUBLICATION)이 남아 다시 보낼 수 있다(ADR-0010).
 *
 * <p>예약 · 자동이체도 이체 엔진이 TransferSettled 로 발행하지만 refId 가 transfer_id 라 알림 규약(예약 →
 * scheduled_transfer_id, 자동 → execution_id)과 맞지 않는다. 그 알림은 배치가 결과마다 발행하는 이벤트로
 * 받으므로(#707) 여기서는 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationTransferSettledListener {

    private static final Set<String> CUSTOMER_INITIATED_TX_TYPES = Set.of("IMMEDIATE_TRANSFER", "INTERBANK_TRANSFER");

    private final NotificationCommandUseCase notificationCommandUseCase;

    @ApplicationModuleListener
    public void on(TransferSettled event) {
        boolean isCustomerInitiatedTransfer = CUSTOMER_INITIATED_TX_TYPES.contains(event.txType());
        if (!isCustomerInitiatedTransfer) {
            return;
        }
        // 타행 확정 경로는 출금계좌 조회가 비면 customerId 없이 발행한다. 다시 보내도 수신 고객을 알 수 없어 재제출
        // 대상으로 남기지 않는다. 알림 한 건이 사라지는 데이터 결함이라 모니터링(PH-47)이 보는 ERROR 로 남긴다
        if (event.customerId() == null) {
            log.error("수신 고객이 없어 이체 결과 알림 유실 - transferId={}, txType={}", event.refId(), event.txType());
            return;
        }
        notificationCommandUseCase.createTransferResultNotification(NotificationTransferResultCommand.builder()
                .customerId(event.customerId())
                .type(NotificationType.TRANSFER)
                .refId(event.refId())
                .isSucceeded(event.status() == ProcessResultStatus.SUCCESS)
                .errorCode(event.errorCode())
                .amount(event.amount())
                .counterpartyName(event.counterpartyName())
                .occurredAt(event.occurredAt())
                .build());
    }
}
