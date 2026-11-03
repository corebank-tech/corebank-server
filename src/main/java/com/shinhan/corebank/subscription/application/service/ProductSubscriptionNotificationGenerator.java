package com.shinhan.corebank.subscription.application.service;

import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.api.NotificationType;
import com.shinhan.corebank.subscription.domain.ProductSubscription;

/** 완료된 상품가입을 고객 알림으로 만든다. 참조는 가입 건 ID 이고, 계좌번호는 마스킹해서만 싣는다. */
final class ProductSubscriptionNotificationGenerator {

    static NotificationRequest generate(ProductSubscription subscription, String productName, String accountNumber) {
        String content = productName + " 가입이 완료되었습니다. 계좌번호 " + MaskingUtil.maskAccountNumber(accountNumber);
        return NotificationRequest.builder()
                .customerId(subscription.getCustomerId())
                .type(NotificationType.PRODUCT_SUBSCRIPTION)
                .refId(subscription.getSubscriptionId())
                .title("상품가입 완료")
                .content(content)
                .occurredAt(subscription.getSubscribedAt())
                .build();
    }
}
