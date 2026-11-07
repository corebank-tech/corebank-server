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
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
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
                description = "`CMN0001` 음수 page 또는 숫자·불리언이 아닌 값 · `CMN0005` 허용되지 않은 size",
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

    @Operation(
            operationId = "markNotificationAsRead",
            summary = "알림 읽음 처리",
            description =
                    """
                    로그인 고객의 알림을 읽음으로 표시하고 남은 미읽음 건수를 반환한다.
                    이미 읽은 알림을 다시 처리해도 성공하며 최초 읽음 일시를 유지한다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "알림 읽음 처리 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0001` 잘못된 알림 ID 또는 Idempotency-Key 형식 · `CMN0002` Idempotency-Key 누락",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`CMN0102` 다른 고객의 알림",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`NTF0201` 알림을 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`CMN0301` 동일 요청 처리 중 · `CMN0302` 같은 키를 다른 요청에 재사용",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<NotificationReadResponse>> markNotificationAsRead(
            @Parameter(description = "알림 ID", required = true, example = "1") @Positive Long notificationId,
            @Parameter(
                            description = "멱등키(UUID v4). 동일 키와 동일 요청 재요청 시 저장된 응답 반환",
                            required = true,
                            example = "550e8400-e29b-41d4-a716-446655440000")
                    String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedCustomer customer);
}
