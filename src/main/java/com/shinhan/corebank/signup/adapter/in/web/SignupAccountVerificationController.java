package com.shinhan.corebank.signup.adapter.in.web;

import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.signup.adapter.in.web.dto.VerifySignupAccountRequest;
import com.shinhan.corebank.signup.adapter.in.web.dto.VerifySignupAccountResponse;
import com.shinhan.corebank.signup.application.port.in.VerifySignupAccountResult;
import com.shinhan.corebank.signup.application.port.in.VerifySignupAccountUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 회원가입 실명·계좌 인증 HTTP API를 제공한다.
@Tag(name = "회원가입 실명·계좌 인증")
@RestController
@RequestMapping("/auth")
public class SignupAccountVerificationController {

    private final VerifySignupAccountUseCase verifySignupAccountUseCase;

    public SignupAccountVerificationController(
            VerifySignupAccountUseCase verifySignupAccountUseCase
    ) {
        this.verifySignupAccountUseCase = verifySignupAccountUseCase;
    }

    @Operation(summary = "회원가입 실명·계좌 인증")
    @PostMapping("/verify-account")
    public ApiResponse<VerifySignupAccountResponse> verifyAccount(
            @Valid @RequestBody VerifySignupAccountRequest request
    ) {
        VerifySignupAccountResult result = verifySignupAccountUseCase.verify(
                request.toCommand()
        );

        return ApiResponse.success(
                VerifySignupAccountResponse.from(result)
        );
    }
}
