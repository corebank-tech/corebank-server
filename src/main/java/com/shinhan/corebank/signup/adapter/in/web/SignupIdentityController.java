package com.shinhan.corebank.signup.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.signup.adapter.in.web.dto.CheckUserIdRequest;
import com.shinhan.corebank.signup.adapter.in.web.dto.CheckUserIdResponse;
import com.shinhan.corebank.signup.adapter.in.web.dto.IssueEmailVerificationRequest;
import com.shinhan.corebank.signup.adapter.in.web.dto.IssueEmailVerificationResponse;
import com.shinhan.corebank.signup.adapter.in.web.dto.VerifyEmailRequest;
import com.shinhan.corebank.signup.adapter.in.web.dto.VerifyEmailResponse;
import com.shinhan.corebank.signup.application.port.in.CheckUserIdCommand;
import com.shinhan.corebank.signup.application.port.in.CheckUserIdResult;
import com.shinhan.corebank.signup.application.port.in.CheckUserIdUseCase;
import com.shinhan.corebank.signup.application.port.in.IssueEmailVerificationResult;
import com.shinhan.corebank.signup.application.port.in.IssueEmailVerificationUseCase;
import com.shinhan.corebank.signup.application.port.in.VerifyEmailResult;
import com.shinhan.corebank.signup.application.port.in.VerifyEmailUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 아이디 중복확인과 이메일 인증 요청을 HTTP API로 제공한다.
@Tag(name = "회원가입", description = "회원가입 단계별 인증·입력 검증·가입 완료 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class SignupIdentityController {

    private final CheckUserIdUseCase checkUserIdUseCase;
    private final IssueEmailVerificationUseCase issueEmailVerificationUseCase;
    private final VerifyEmailUseCase verifyEmailUseCase;

    @Operation(
            operationId = "checkUserId",
            summary = "회원가입 아이디 중복확인",
            description = "로그인 아이디 형식과 중복 여부를 확인하고 userIdCheckToken을 발급한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "사용 가능한 아이디 확인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0001` 요청 형식 오류 · `ATH0004` 아이디 형식 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`ATH0301` 이미 사용 중인 아이디",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/check-id")
    public ApiResponse<CheckUserIdResponse> checkUserId(@Valid @RequestBody CheckUserIdRequest request) {
        CheckUserIdResult result = checkUserIdUseCase.check(new CheckUserIdCommand(request.userId()));

        return ApiResponse.success(CheckUserIdResponse.from(result), "사용 가능한 ID입니다. 사용하시겠습니까?");
    }

    @Operation(
            operationId = "issueEmailVerification",
            summary = "이메일 인증번호 발급",
            description = "회원가입 또는 이메일 변경에 사용할 인증번호와 emailVerificationId를 발급한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이메일 인증번호 발급 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0001` 이메일 또는 인증 목적 형식 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`ATH0302` 회원가입에 이미 사용 중인 이메일",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/email-verifications")
    public ApiResponse<IssueEmailVerificationResponse> issueEmailVerification(
            @Valid @RequestBody IssueEmailVerificationRequest request) {
        IssueEmailVerificationResult result = issueEmailVerificationUseCase.issue(request.toCommand());

        return ApiResponse.success(IssueEmailVerificationResponse.from(result), "인증번호가 발급되었습니다.");
    }

    @Operation(
            operationId = "verifyEmail",
            summary = "이메일 인증번호 검증",
            description = "이메일 인증번호를 검증하고 emailVerificationToken을 발급한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이메일 인증 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0001` 요청 형식 오류 · `ATH0007` 인증번호 불일치 · `ATH0008` 인증번호 만료",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`ATH0202` 이메일 인증 요청을 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/email-verifications/{emailVerificationId}/verify")
    public ApiResponse<VerifyEmailResponse> verifyEmail(
            @Parameter(description = "이메일 인증 요청 식별자", required = true, example = "EMAIL_REQ_7xP9qK2RmY5vLw8Z")
                    @PathVariable
                    String emailVerificationId,
            @Valid @RequestBody VerifyEmailRequest request) {
        VerifyEmailResult result = verifyEmailUseCase.verify(request.toCommand(emailVerificationId));

        return ApiResponse.success(VerifyEmailResponse.from(result), "이메일 인증이 완료되었습니다.");
    }
}
