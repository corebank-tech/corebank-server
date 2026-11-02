package com.shinhan.corebank.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.api.NotificationType;
import com.shinhan.corebank.subscription.domain.ProductSubscription;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductSubscriptionNotificationGeneratorTest {

    private static final Long CUSTOMER_ID = 7081L;
    private static final Long SUBSCRIPTION_ID = 7_081_001L;
    private static final LocalDateTime SUBSCRIBED_AT = LocalDateTime.of(2026, 10, 29, 9, 0);
    private static final String ACCOUNT_NUMBER = "220000000001";

    @Test
    @DisplayName("가입 건 ID 로 상품명과 마스킹한 개설 계좌번호의 상품가입 완료 알림을 만든다")
    void generate_productSubscriptionNotification() {
        // given
        ProductSubscription subscription = ProductSubscription.builder()
                .subscriptionId(SUBSCRIPTION_ID)
                .customerId(CUSTOMER_ID)
                .subscribedAt(SUBSCRIBED_AT)
                .build();

        // when
        NotificationRequest notification =
                ProductSubscriptionNotificationGenerator.generate(subscription, "청년 희망 적금", ACCOUNT_NUMBER);

        // then
        assertThat(notification.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(notification.type()).isEqualTo(NotificationType.PRODUCT_SUBSCRIPTION);
        assertThat(notification.refId()).isEqualTo(SUBSCRIPTION_ID);
        assertThat(notification.title()).isEqualTo("상품가입 완료");
        assertThat(notification.content()).isEqualTo("청년 희망 적금 가입이 완료되었습니다. 계좌번호 220******001");
        assertThat(notification.content()).doesNotContain(ACCOUNT_NUMBER);
        assertThat(notification.occurredAt()).isEqualTo(SUBSCRIBED_AT);
    }
}
