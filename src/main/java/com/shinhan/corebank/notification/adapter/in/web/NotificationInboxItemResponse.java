package com.shinhan.corebank.notification.adapter.in.web;

import com.shinhan.corebank.notification.domain.NotificationInboxEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Builder;

/** 알림 목록 항목. */
@Builder
public record NotificationInboxItemResponse(
        @Schema(description = "알림 ID", example = "1") Long notificationId,
        @Schema(description = "알림 유형 표시명", example = "이체") String typeName,
        @Schema(description = "알림 제목", example = "이체가 정상 처리되었습니다.") String title,
        @Schema(description = "알림 본문", example = "자유입출금에서 김*수님께 500,000원을 이체했습니다.") String content,
        @Schema(description = "읽음 여부", example = "false") boolean read,
        @Schema(description = "최초 읽음 일시. 미읽음이면 null", example = "2026-11-07T10:05:00+09:00", nullable = true)
                OffsetDateTime readAt,
        @Schema(description = "발생 일시", example = "2026-11-07T10:00:00+09:00") OffsetDateTime occurredAt) {

    public static NotificationInboxItemResponse from(NotificationInboxEntry entry) {
        return NotificationInboxItemResponse.builder()
                .notificationId(entry.notificationId())
                .typeName(entry.type().getDisplayName())
                .title(entry.title())
                .content(entry.content())
                .read(entry.isRead())
                .readAt(entry.readAt() == null ? null : NotificationResponseTime.toOffsetDateTime(entry.readAt()))
                .occurredAt(NotificationResponseTime.toOffsetDateTime(entry.occurredAt()))
                .build();
    }
}
