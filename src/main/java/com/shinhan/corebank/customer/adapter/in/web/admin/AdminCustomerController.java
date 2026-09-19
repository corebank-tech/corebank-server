package com.shinhan.corebank.customer.adapter.in.web.admin;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotencyFingerprint;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.common.response.PageResponse;
import com.shinhan.corebank.common.util.PageableResolver;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerCommandUseCase;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerOperationCommand;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerQueryUseCase;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerSearchQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

// 관리자 고객 계정 운영(#449, PH-97). /admin/** 인가는 SecurityConfig의 임시 허용 목록(#448)이 맡는다.
@RestController
@RequestMapping("/admin/customers")
@RequiredArgsConstructor
@Validated
@Tag(name = "관리자 고객 계정 운영", description = "관리자의 고객 검색·잠금 해제·로그인 비밀번호 초기화 API")
public class AdminCustomerController {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(5, 10, 20, 30, 50);

    private final AdminCustomerQueryUseCase adminCustomerQueryUseCase;
    private final AdminCustomerCommandUseCase adminCustomerCommandUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @GetMapping
    @Operation(
            operationId = "searchAdminCustomers",
            summary = "관리자 고객 검색",
            description = "조건을 하나 이상 받아 고객을 찾는다. 아이디·성명은 앞부분 일치, 이메일은 정확 일치다. "
                    + "조건 없는 전체 목록은 제공하지 않는다. 응답의 개인정보는 서버에서 마스킹된다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "검색 성공. 0건이면 items가 빈 배열"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0002` 검색 조건 없음 · `CMN0001` 성명 1글자 · `CMN0005` 지원하지 않는 페이지 크기 · "
                        + "`CMN0006` 전체조회(all=true) 결과 100건 초과",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`CMN0102` 관리자 허용 목록 밖",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<PageResponse<AdminCustomerSummaryResponse>> search(
            @Parameter(description = "로그인 아이디 앞부분", example = "hong") @RequestParam(required = false) String userId,
            @Parameter(description = "성명 앞부분. 두 글자 이상", example = "홍길") @RequestParam(required = false) String userName,
            @Parameter(description = "이메일 전체(정확 일치)", example = "hong@corebank.example.com")
                    @RequestParam(required = false)
                    String email,
            @Parameter(description = "잠금 여부", example = "true") @RequestParam(required = false) Boolean accountLocked,
            @Parameter(description = "페이지 번호(0부터 시작). all=true면 무시됨", example = "0") @RequestParam(defaultValue = "0")
                    int page,
            @Parameter(description = "페이지 크기. 5/10/20/30/50 중 하나. all=true면 무시됨", example = "10")
                    @RequestParam(defaultValue = "10")
                    int size,
            @Parameter(description = "true면 조건에 맞는 전체 건을 반환(최대 100건)", example = "false")
                    @RequestParam(defaultValue = "false")
                    boolean all) {
        var pageable = PageableResolver.resolve(page, size, all, ALLOWED_PAGE_SIZES);
        var result = adminCustomerQueryUseCase.search(
                new AdminCustomerSearchQuery(userId, userName, email, accountLocked), pageable);
        return ApiResponse.success(PageResponse.from(result, AdminCustomerSummaryResponse::from));
    }

    @GetMapping("/{customerId}")
    @Operation(operationId = "getAdminCustomerDetail", summary = "관리자 고객 상세", description = "개인정보는 마스킹되어 내려간다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`ATH0201` 대상 고객 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<AdminCustomerDetailResponse> getDetail(
            @Parameter(description = "고객 내부 식별자", required = true, example = "1") @PathVariable @Positive
                    Long customerId) {
        return ApiResponse.success(AdminCustomerDetailResponse.from(adminCustomerQueryUseCase.getDetail(customerId)));
    }

    @PostMapping("/{customerId}/unlock")
    @Operation(
            operationId = "unlockAdminCustomer",
            summary = "관리자 계정 잠금 해제",
            description = "잠금을 풀고 연속 로그인 실패 횟수를 0으로 되돌린다(REQ-AUTH-026·035). 이미 풀린 계정도 200이다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "해제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`CMN0102` 자기 자신 대상 또는 관리자 허용 목록 밖",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`ATH0201` 대상 고객 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`CMN0301`/`CMN0302` 멱등키 충돌",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<AdminUnlockResponse>> unlock(
            @Parameter(description = "고객 내부 식별자", required = true, example = "1") @PathVariable @Positive
                    Long customerId,
            @Parameter(description = "멱등키", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            HttpServletRequest httpRequest) {
        Long adminCustomerId = currentCustomerProvider.getCurrentCustomerId();
        var command = new AdminCustomerOperationCommand(adminCustomerId, customerId, httpRequest.getRemoteAddr());

        // endpoint와 지문에 대상 id를 넣어 같은 키로 다른 고객을 풀면 CMN0302가 나게 한다.
        return idempotentRequestExecutor.execute(
                idempotencyKey,
                adminCustomerId,
                "POST /admin/customers/" + customerId + "/unlock",
                IdempotencyFingerprint.of(adminCustomerId, null, Map.of("targetCustomerId", customerId)),
                new TypeReference<>() {},
                () -> ApiResponse.success(
                        AdminUnlockResponse.from(adminCustomerCommandUseCase.unlock(command)), "계정 잠금이 해제되었습니다."));
    }

    // 멱등키를 적용하지 않는다 — 저장된 응답(response_snapshot)에 임시 비밀번호 평문이 24시간 남기 때문이다(§7-3).
    @PostMapping("/{customerId}/password-reset")
    @Operation(
            operationId = "resetAdminCustomerPassword",
            summary = "관리자 로그인 비밀번호 초기화",
            description = "임시 비밀번호를 발급해 이 응답으로 한 번만 돌려주고, 계정 잠금도 해제한다. "
                    + "멱등키를 쓰지 않으며, 다시 요청하면 새 임시 비밀번호가 발급되고 이전 값은 쓸 수 없다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "초기화 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`CMN0102` 자기 자신 대상 또는 관리자 허용 목록 밖",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`ATH0201` 대상 고객 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<AdminPasswordResetResponse> resetPassword(
            @Parameter(description = "고객 내부 식별자", required = true, example = "1") @PathVariable @Positive
                    Long customerId,
            HttpServletRequest httpRequest) {
        Long adminCustomerId = currentCustomerProvider.getCurrentCustomerId();
        var result = adminCustomerCommandUseCase.resetPassword(
                new AdminCustomerOperationCommand(adminCustomerId, customerId, httpRequest.getRemoteAddr()));
        return ApiResponse.success(AdminPasswordResetResponse.from(result), "로그인 비밀번호가 초기화되었습니다.");
    }
}
