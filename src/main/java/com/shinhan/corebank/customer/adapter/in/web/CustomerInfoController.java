package com.shinhan.corebank.customer.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.customer.application.port.in.CustomerInfoQueryUseCase;
import com.shinhan.corebank.customer.application.port.in.CustomerInfoResult;
import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoResult;
import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

// 로그인 고객의 기본정보 조회 API를 제공한다.
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
@Tag(name = "고객", description = "로그인 고객의 기본정보 조회·변경 API")
public class CustomerInfoController {

    private static final String UPDATE_ENDPOINT = "PATCH /customers/me";
    private static final String UPDATE_SUCCESS_MESSAGE = "고객정보가 변경되었습니다.";

    private final CustomerInfoQueryUseCase customerInfoQueryUseCase;
    private final UpdateCustomerInfoUseCase updateCustomerInfoUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @GetMapping("/me")
    @Operation(operationId = "getMe", summary = "내 고객정보 조회", description = "로그인한 고객의 기본정보를 개인정보 마스킹 규칙에 따라 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "고객정보 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<CustomerInfoResponse> getCustomerInfo() {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        CustomerInfoResult result = customerInfoQueryUseCase.getCustomerInfo(customerId);

        return ApiResponse.success(CustomerInfoResponse.from(result));
    }

    // 로그인 고객의 연락처를 멱등하게 변경한다.
    @PatchMapping("/me")
    @Operation(
            operationId = "updateMe",
            summary = "내 고객정보 변경",
            description = "휴대폰 번호 또는 이메일을 변경한다. 이메일 변경 시 유효한 이메일 인증 토큰이 필요하다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "고객정보 변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0001` 요청 형식 오류 · `CMN0002` 필수값 또는 멱등키 누락 · `MYP0001` 휴대폰 번호 형식 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`ATH0103` 이메일 인증 토큰 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`ATH0302` 이메일 중복 · `CMN0301`/`CMN0302` 멱등키 충돌",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<CustomerInfoUpdateResponse>> updateCustomerInfo(
            @Parameter(description = "UUID v4 멱등키", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @RequestBody CustomerInfoUpdateRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();

        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                UPDATE_ENDPOINT,
                new CustomerInfoUpdateFingerprint(
                        customerId, request.phoneNumber(), request.email(), request.emailVerificationToken()),
                new TypeReference<>() {},
                () -> {
                    UpdateCustomerInfoResult result = updateCustomerInfoUseCase.update(request.toCommand(customerId));
                    return ApiResponse.success(CustomerInfoUpdateResponse.from(result), UPDATE_SUCCESS_MESSAGE);
                });
    }

    // 고객·연락처·이메일 인증 토큰을 포함한 고객정보 변경 요청 지문이다.
    private record CustomerInfoUpdateFingerprint(
            Long customerId, String phoneNumber, String email, String emailVerificationToken) {}
}
