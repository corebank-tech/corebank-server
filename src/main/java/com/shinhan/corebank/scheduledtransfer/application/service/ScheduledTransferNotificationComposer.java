package com.shinhan.corebank.scheduledtransfer.application.service;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.api.NotificationType;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * 확정된 예약이체를 고객 알림으로 만든다(#707). 참조는 예약 건 ID 다.
 * 실패 사유는 이체 엔진이 고객 조치 가능으로 판정한 것만 붙이고, 없으면 기본 문장만 쓴다.
 */
final class ScheduledTransferNotificationComposer {

    static NotificationRequest compose(
            ScheduledTransfer scheduledTransfer,
            ProcessResultStatus status,
            Optional<String> customerFailureReason,
            LocalDateTime occurredAt) {
        boolean isSucceeded = status == ProcessResultStatus.SUCCESS;
        String recipientPhrase = recipientPhrase(scheduledTransfer.getAmount(), scheduledTransfer.getPayeeName());
        String content = isSucceeded
                ? recipientPhrase + "을 보냈습니다."
                : recipientPhrase + "을 보내지 못했습니다."
                        + customerFailureReason.map(reason -> " " + reason).orElse("");
        return NotificationRequest.builder()
                .customerId(scheduledTransfer.getCustomerId())
                .type(NotificationType.SCHEDULED_TRANSFER)
                .refId(scheduledTransfer.getScheduledTransferId())
                .title(isSucceeded ? "예약이체 완료" : "예약이체 실패")
                .content(content)
                .occurredAt(occurredAt)
                .build();
    }

    private static String recipientPhrase(long amount, String payeeName) {
        String groupedAmount = String.format(Locale.ROOT, "%,d원", amount);
        return payeeName == null ? groupedAmount : MaskingUtil.maskName(payeeName) + "님에게 " + groupedAmount;
    }
}
