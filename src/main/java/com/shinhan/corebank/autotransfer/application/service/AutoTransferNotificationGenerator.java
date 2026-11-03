package com.shinhan.corebank.autotransfer.application.service;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.api.NotificationType;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * 확정된 자동이체 회차를 고객 알림으로 만든다(#707). 참조는 회차 ID, 금액은 회차에 저장된 금액이다.
 * 실패 사유는 이체 엔진이 고객 조치 가능으로 판정한 것만 붙인다.
 */
final class AutoTransferNotificationGenerator {

    static NotificationRequest generate(
            AutoTransfer autoTransfer,
            AutoTransferExecution execution,
            ProcessResultStatus status,
            Optional<String> customerFailureReason,
            LocalDateTime occurredAt) {
        boolean isSucceeded = status == ProcessResultStatus.SUCCESS;
        String recipientPhrase = recipientPhrase(execution.getAmount(), autoTransfer.getPayeeName());
        String content = isSucceeded
                ? recipientPhrase + "을 보냈습니다."
                : recipientPhrase + "을 보내지 못했습니다."
                        + customerFailureReason.map(reason -> " " + reason).orElse("");
        return NotificationRequest.builder()
                .customerId(autoTransfer.getCustomerId())
                .type(NotificationType.AUTO_TRANSFER)
                .refId(execution.getExecutionId())
                .title(isSucceeded ? "자동이체 완료" : "자동이체 실패")
                .content(content)
                .occurredAt(occurredAt)
                .build();
    }

    private static String recipientPhrase(long amount, String payeeName) {
        String groupedAmount = String.format(Locale.ROOT, "%,d원", amount);
        return payeeName == null ? groupedAmount : MaskingUtil.maskName(payeeName) + "님에게 " + groupedAmount;
    }
}
