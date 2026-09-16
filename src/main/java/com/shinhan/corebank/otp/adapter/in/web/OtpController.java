package com.shinhan.corebank.otp.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.otp.application.port.in.IssueOtpResult;
import com.shinhan.corebank.otp.application.port.in.IssueOtpUseCase;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpResult;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpUseCase;
import com.shinhan.corebank.otp.config.OtpProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 로그인 고객에게 OTP 발급과 숫자 6자리 검증 API를 제공한다.
@RestController
@RequestMapping("/otp")
@RequiredArgsConstructor
@Tag(name = "OTP", description = "거래 OTP 발급·검증 API")
public class OtpController {

    private final CurrentCustomerProvider currentCustomerProvider;
    private final IssueOtpUseCase issueOtpUseCase;
    private final VerifyOtpUseCase verifyOtpUseCase;
    private final OtpProperties otpProperties;

    @PostMapping("/issue")
    @Operation(
            operationId = "issueOtp",
            summary = "거래 OTP 발급",
            description = "로그인 고객의 거래 유형과 핵심 거래정보를 묶어 숫자 6자리 OTP를 발급한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP 발급 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0001` 거래 유형 또는 거래정보 형식 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`CMN0303` OTP 발급 상태가 다른 요청에 의해 변경됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<IssueOtpResponse> issue(@Valid @RequestBody IssueOtpRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        IssueOtpResult result = issueOtpUseCase.issue(request.toCommand(customerId));
        return ApiResponse.success(IssueOtpResponse.from(result, otpProperties.exposeCode()), "OTP가 성공적으로 발급되었습니다.");
    }

    @PostMapping("/verify")
    @Operation(
            operationId = "verifyOtp",
            summary = "거래 OTP 검증",
            description = "발급된 OTP를 검증하고 최종 거래에서 한 번 사용할 수 있는 otpAuthToken을 발급한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP 검증 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0001` 요청 형식 오류 · `OTP0001` OTP 번호 불일치",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`CMN0102` 다른 고객의 OTP 요청 · `OTP0103` OTP 오류 횟수 초과 · `OTP0104` OTP 만료",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`OTP0201` OTP 요청을 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<VerifyOtpResponse> verify(@Valid @RequestBody VerifyOtpRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        VerifyOtpResult result = verifyOtpUseCase.verify(request.toCommand(customerId));
        return ApiResponse.success(VerifyOtpResponse.success(result), "OTP 검증이 완료되었습니다.");
    }
}
