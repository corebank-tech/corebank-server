package com.shinhan.corebank.notification.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/** 알림함 API 의 Swagger 명세. 제약은 메서드 검증 규칙상 여기에만 둔다. 매핑 애너테이션은 구현체가 갖는다. */
@Tag(name = "알림", description = "알림 목록·읽음·미읽음 건수 API")
public interface NotificationControllerDocs {

    @Operation(
            operationId = "getNotifications",
            summary = "알림 목록 조회",
            description =
                    """
                    로그인 고객의 알림을 발생 일시 최신순으로 조회한다. 같은 일시면 알림 ID가 큰 것이 먼저다.
                    unreadCount는 현재 페이지가 아니라 고객의 전체 미읽음 건수다.
                    all=true면 page·size를 무시하고 전체를 반환한다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "알림 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0001` 음수 page · `CMN0005` 허용되지 않은 size",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ApiResponse<NotificationListResponse> getNotifications(
            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0") int page,
            @Parameter(description = "페이지 크기. 5·10·20·30·50", example = "10") int size,
            @Parameter(description = "전체 조회 여부", example = "false") boolean all,
            @AuthenticationPrincipal AuthenticatedCustomer customer);

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
