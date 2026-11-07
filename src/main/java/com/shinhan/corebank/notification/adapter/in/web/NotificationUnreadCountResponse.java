package com.shinhan.corebank.notification.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** 미읽음 알림 건수 응답. */
public record NotificationUnreadCountResponse(@Schema(description = "전체 미읽음 건수", example = "3") long unreadCount) {}
