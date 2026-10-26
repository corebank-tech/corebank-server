package com.shinhan.corebank.notification.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 업무 도메인이 고객에게 남기려는 알림 한 건. 제목과 본문은 발행 도메인이 금액 표시 · 마스킹 · 고객용 실패 사유까지
 * 끝낸 문구이고, notification 은 이 값을 해석하지 않고 저장한다. 같은 업무(type + refId)의 알림은 한 건만 남는다.
 * 길이 제한은 notification 테이블 컬럼(title 100 · content 500)과 같다.
 */
@Builder
public record NotificationRequest(
        @NotNull Long customerId,
        @NotNull NotificationType type,
        @NotNull Long refId,
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 500) String content,
        @NotNull LocalDateTime occurredAt) {}
