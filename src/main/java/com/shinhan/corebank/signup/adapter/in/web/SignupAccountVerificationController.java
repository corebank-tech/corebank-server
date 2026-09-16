package com.shinhan.corebank.signup.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.signup.adapter.in.web.dto.VerifySignupAccountRequest;
import com.shinhan.corebank.signup.adapter.in.web.dto.VerifySignupAccountResponse;
import com.shinhan.corebank.signup.application.port.in.VerifySignupAccountResult;
import com.shinhan.corebank.signup.application.port.in.VerifySignupAccountUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 회원가입 실명·계좌 인증 HTTP API를 제공한다.
@Tag(name = "회원가입", description = "회원가입 단계별 인증·입력 검증·가입 완료 API")
@RestController
@RequestMapping("/auth")
public class SignupAccountVerificationController {

    private final VerifySignupAccountUseCase verifySignupAccountUseCase;

    public SignupAccountVerificationController(VerifySignupAccountUseCase verifySignupAccountUseCase) {
        this.verifySignupAccountUseCase = verifySignupAccountUseCase;
    }

    @Operation(
            operationId = "verifySignupAccount",
            summary = "회원가입 실명·계좌 인증",
            description = "기존 은행 고객의 성명·생년월일·계좌번호·계좌비밀번호를 검증하고 accountAuthToken을 발급한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "실명·계좌 인증 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0001` 요청 형식 오류 · `ATH0009` 실명 또는 계좌정보 불일치",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`ATH0102` 계좌비밀번호 오류 횟수 초과로 계좌 잠금",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`ATH0303` 인증된 원장 고객이 이미 인터넷뱅킹에 가입됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/verify-account")
    public ApiResponse<VerifySignupAccountResponse> verifyAccount(
            @Valid @RequestBody VerifySignupAccountRequest request) {
        VerifySignupAccountResult result = verifySignupAccountUseCase.verify(request.toCommand());

        return ApiResponse.success(VerifySignupAccountResponse.from(result));
    }
}
