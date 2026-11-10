package com.shinhan.corebank.notification.application.port.in.dto;

import com.shinhan.corebank.notification.domain.NotificationInboxEntry;
import java.time.LocalDateTime;
import lombok.Builder;
import org.springframework.data.domain.Page;

/** 알림 목록 한 페이지. unreadCount 는 페이지와 무관한 고객 전체 미읽음 건수다. */
@Builder
public record NotificationListResult(
        LocalDateTime asOf, long unreadCount, Page<NotificationInboxEntry> notifications) {}
