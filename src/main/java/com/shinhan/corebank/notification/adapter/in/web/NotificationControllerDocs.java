package com.shinhan.corebank.notification.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/** 알림함 API 의 Swagger 명세. 제약은 메서드 검증 규칙상 여기에만 둔다. 매핑 애너테이션은 구현체가 갖는다. */
@Tag(name = "알림", description = "알림 목록·읽음·미읽음 건수 API")
public interface NotificationControllerDocs {
    @Operation(
            operationId = "getUnreadNotificationCount",
            summary = "미읽음 알림 건수 조회",
            description = "헤더 배지에 쓸 로그인 고객의 전체 미읽음 알림 건수를 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "미읽음 건수 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ApiResponse<NotificationUnreadCountResponse> getUnreadNotificationCount(
            @AuthenticationPrincipal AuthenticatedCustomer customer);
}
