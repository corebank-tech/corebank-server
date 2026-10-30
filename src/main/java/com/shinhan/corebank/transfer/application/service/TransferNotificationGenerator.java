package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.api.NotificationType;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 확정된 이체 한 건을 고객 알림으로 만든다(PR #723 리뷰 — 문구는 업무 도메인이 만든다). 실패 사유는 고객이 직접
 * 조치할 수 있는 오류만 그 오류코드의 문구로 붙이고, 나머지는 기본 문장만 쓴다.
 */
final class TransferNotificationGenerator {

    static NotificationRequest generate(Transfer transfer, Long customerId, LocalDateTime occurredAt) {
        boolean isSucceeded = transfer.getStatus() == ProcessResultStatus.SUCCESS;
        String recipientPhrase = recipientPhrase(transfer.getAmount(), transfer.getPayeeName());
        String content = isSucceeded
                ? recipientPhrase + "을 보냈습니다."
                : recipientPhrase + "을 보내지 못했습니다." + customerReasonSuffix(transfer.getErrorCode());
        return NotificationRequest.builder()
                .customerId(customerId)
                .type(NotificationType.TRANSFER)
                .refId(transfer.getTransferId())
                .title(isSucceeded ? "이체 완료" : "이체 실패")
                .content(content)
                .occurredAt(occurredAt)
                .build();
    }

    private static String recipientPhrase(long amount, String payeeName) {
        String groupedAmount = String.format(Locale.ROOT, "%,d원", amount);
        return payeeName == null ? groupedAmount : MaskingUtil.maskName(payeeName) + "님에게 " + groupedAmount;
    }

    private static String customerReasonSuffix(String errorCode) {
        return TransferErrorCode.customerActionableMessage(errorCode)
                .map(reason -> " " + reason)
                .orElse("");
    }
}
