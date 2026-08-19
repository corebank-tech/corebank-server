package com.shinhan.corebank.signup.adapter.in.web;

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
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 아이디 중복확인과 이메일 인증 요청을 HTTP API로 제공한다.
@Tag(name = "회원가입 식별정보 인증")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class SignupIdentityController {

    private final CheckUserIdUseCase checkUserIdUseCase;
    private final IssueEmailVerificationUseCase issueEmailVerificationUseCase;
    private final VerifyEmailUseCase verifyEmailUseCase;

    @Operation(summary = "회원가입 아이디 중복확인")
    @PostMapping("/check-id")
    public ApiResponse<CheckUserIdResponse> checkUserId(
            @Valid @RequestBody CheckUserIdRequest request
    ) {
        CheckUserIdResult result = checkUserIdUseCase.check(
                new CheckUserIdCommand(request.userId())
        );

        return ApiResponse.success(
                CheckUserIdResponse.from(result),
                "사용 가능한 ID입니다. 사용하시겠습니까?"
        );
    }

    @Operation(summary = "회원가입 이메일 인증번호 발급")
    @PostMapping("/email-verifications")
    public ApiResponse<IssueEmailVerificationResponse> issueEmailVerification(
            @Valid @RequestBody IssueEmailVerificationRequest request
    ) {
        IssueEmailVerificationResult result =
                issueEmailVerificationUseCase.issue(request.toCommand());

        return ApiResponse.success(
                IssueEmailVerificationResponse.from(result),
                "인증번호가 발급되었습니다."
        );
    }

    @Operation(summary = "회원가입 이메일 인증번호 검증")
    @PostMapping("/email-verifications/{emailVerificationId}/verify")
    public ApiResponse<VerifyEmailResponse> verifyEmail(
            @PathVariable String emailVerificationId,
            @Valid @RequestBody VerifyEmailRequest request
    ) {
        VerifyEmailResult result = verifyEmailUseCase.verify(
                request.toCommand(emailVerificationId)
        );

        return ApiResponse.success(
                VerifyEmailResponse.from(result),
                "이메일 인증이 완료되었습니다."
        );
    }
}
