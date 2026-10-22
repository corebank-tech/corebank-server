package com.shinhan.corebank.notification.domain;

import java.time.LocalDateTime;
import lombok.Builder;

/** 고객에게 남길 알림 한 건. */
@Builder
public record Notification(
        Long customerId, NotificationType type, Long refId, String title, String content, LocalDateTime occurredAt) {}
