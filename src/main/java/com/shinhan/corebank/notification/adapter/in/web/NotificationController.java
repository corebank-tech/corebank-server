package com.shinhan.corebank.notification.adapter.in.web;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.idempotency.IdempotencyFingerprint;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.notification.application.port.in.NotificationMarkReadUseCase;
import com.shinhan.corebank.notification.application.port.in.NotificationQueryUseCase;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

/** 고객 본인의 알림함 API. 고객 식별자는 세션에서만 얻는다(REQ-NFR-007). */
@RestController
@RequestMapping("/notifications")
@Validated
@RequiredArgsConstructor
public class NotificationController implements NotificationControllerDocs {

    private final NotificationQueryUseCase notificationQueryUseCase;
    private final NotificationMarkReadUseCase notificationMarkReadUseCase;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @Override
    @GetMapping
    public ApiResponse<NotificationListResponse> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean all,
            @AuthenticationPrincipal AuthenticatedCustomer customer) {
        return ApiResponse.success(NotificationListResponse.from(
                notificationQueryUseCase.getNotifications(customer.customerId(), page, size, all)));
    }

    @Override
    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadCountResponse> getUnreadNotificationCount(
            @AuthenticationPrincipal AuthenticatedCustomer customer) {
        return ApiResponse.success(
                new NotificationUnreadCountResponse(notificationQueryUseCase.getUnreadCount(customer.customerId())));
    }

    @Override
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationReadResponse>> markNotificationAsRead(
            @PathVariable Long notificationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedCustomer customer) {
        final Long customerId = customer.customerId();

        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                "PATCH /notifications/{notificationId}/read",
                IdempotencyFingerprint.of(customerId, null, Map.of("notificationId", notificationId)),
                new TypeReference<>() {},
                () -> ApiResponse.success(NotificationReadResponse.from(
                        notificationMarkReadUseCase.markAsRead(customerId, notificationId))));
    }
}
