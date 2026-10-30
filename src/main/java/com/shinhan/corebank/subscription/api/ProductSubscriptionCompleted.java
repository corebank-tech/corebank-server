package com.shinhan.corebank.subscription.api;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEvent;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 상품가입이 완료됐다. refId 는 subscription_id. 가입은 실패하면 트랜잭션째 롤백돼 실패 알림이 없으므로
 * status 는 항상 SUCCESS, errorCode 는 null 이다(#396).
 */
@Builder
public record ProductSubscriptionCompleted(
        Long customerId,
        Long refId,
        ProcessResultStatus status,
        String errorCode,
        LocalDateTime occurredAt,
        String productName,
        String maskedAccountNumber)
        implements DomainEvent {}
