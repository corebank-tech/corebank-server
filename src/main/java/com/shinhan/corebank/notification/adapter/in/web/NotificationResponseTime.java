package com.shinhan.corebank.notification.adapter.in.web;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 저장 시각은 LocalDateTime(KST 암묵)이라 응답 경계에서 §6-1 대로 오프셋을 붙인다. */
final class NotificationResponseTime {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    static OffsetDateTime toOffsetDateTime(LocalDateTime koreaTime) {
        return koreaTime.atZone(KOREA_ZONE).toOffsetDateTime();
    }
}
