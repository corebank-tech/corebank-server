package com.shinhan.corebank.notification.api;

import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 업무 도메인이 고객에게 남기려는 알림 한 건. 제목과 본문은 발행 도메인이 금액 표시 · 마스킹 · 고객용 실패 사유까지
 * 끝낸 문구이고, notification 은 이 값을 해석하지 않고 저장한다. 같은 업무(type + refId)의 알림은 한 건만 남는다.
 */
@Builder
public record NotificationRequest(
        Long customerId, NotificationType type, Long refId, String title, String content, LocalDateTime occurredAt) {}
