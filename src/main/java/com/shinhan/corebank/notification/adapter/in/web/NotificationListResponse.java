package com.shinhan.corebank.notification.adapter.in.web;

import com.shinhan.corebank.common.response.PageResponse;
import com.shinhan.corebank.notification.application.port.in.dto.NotificationListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;

/** 알림 목록 응답. 페이징 필드는 §6-5 표준을 따른다. */
@Builder
public record NotificationListResponse(
        @Schema(description = "조회 기준 일시", example = "2026-11-07T10:10:00+09:00") OffsetDateTime asOf,
        @Schema(description = "페이지와 무관한 전체 미읽음 건수", example = "3") long unreadCount,
        @Schema(description = "페이지 번호", example = "0") int page,
        @Schema(description = "페이지 크기", example = "10") int size,
        @Schema(description = "전체 건수", example = "37") long totalCount,
        @Schema(description = "전체 페이지 수", example = "4") int totalPages,
        @Schema(description = "알림 목록. 없으면 빈 배열") List<NotificationInboxItemResponse> items) {

    public static NotificationListResponse from(NotificationListResult result) {
        final PageResponse<NotificationInboxItemResponse> notificationPage =
                PageResponse.from(result.notifications(), NotificationInboxItemResponse::from);

        return NotificationListResponse.builder()
                .asOf(NotificationResponseTime.toOffsetDateTime(result.asOf()))
                .unreadCount(result.unreadCount())
                .page(notificationPage.page())
                .size(notificationPage.size())
                .totalCount(notificationPage.totalCount())
                .totalPages(notificationPage.totalPages())
                .items(notificationPage.items())
                .build();
    }
}
