package com.shinhan.corebank.notification.adapter.in.web;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.notification.application.port.in.NotificationQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 고객 본인의 알림함 API. 고객 식별자는 세션에서만 얻는다(REQ-NFR-007). */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationControllerDocs {

    private final NotificationQueryUseCase notificationQueryUseCase;

    @Override
    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadCountResponse> getUnreadNotificationCount(
            @AuthenticationPrincipal AuthenticatedCustomer customer) {
        return ApiResponse.success(
                new NotificationUnreadCountResponse(notificationQueryUseCase.getUnreadCount(customer.customerId())));
    }
}
