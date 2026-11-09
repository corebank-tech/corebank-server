package com.shinhan.corebank.notification.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shinhan.corebank.notification.application.port.in.dto.NotificationMarkReadResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Builder;

/** 알림 읽음 처리 응답. 멱등 재생에서 역직렬화돼도 readAt 이 UTC 로 바뀌지 않게 오프셋을 유지한다. */
@Builder
public record NotificationReadResponse(
        @Schema(description = "알림 ID", example = "1") Long notificationId,
        @Schema(description = "읽음 여부. 성공 응답에서는 항상 true", example = "true") boolean read,
        @Schema(description = "최초 읽음 일시", example = "2026-11-07T10:05:00+09:00")
                @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                OffsetDateTime readAt,
        @Schema(description = "처리 후 남은 전체 미읽음 건수", example = "2") long unreadCount) {

    public static NotificationReadResponse from(NotificationMarkReadResult result) {
        return NotificationReadResponse.builder()
                .notificationId(result.notificationId())
                .read(true)
                .readAt(NotificationResponseTime.toOffsetDateTime(result.readAt()))
                .unreadCount(result.unreadCount())
                .build();
    }
}
