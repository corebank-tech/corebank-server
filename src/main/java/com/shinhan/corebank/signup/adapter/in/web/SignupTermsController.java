package com.shinhan.corebank.signup.adapter.in.web;

import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.signup.adapter.in.web.dto.CheckTermsAgreementRequest;
import com.shinhan.corebank.signup.adapter.in.web.dto.SignupTermResponse;
import com.shinhan.corebank.signup.adapter.in.web.dto.SignupTermsResponse;
import com.shinhan.corebank.signup.adapter.in.web.dto.TermsAuthTokenResponse;
import com.shinhan.corebank.signup.application.port.in.CheckTermsAgreementUseCase;
import com.shinhan.corebank.signup.application.port.in.GetSignupTermsUseCase;
import com.shinhan.corebank.signup.application.port.in.TermsAgreementResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 회원가입 약관 조회와 동의 검증 HTTP API를 제공한다.
@RestController
@RequestMapping("/auth/terms")
public class SignupTermsController {

    private final GetSignupTermsUseCase getSignupTermsUseCase;
    private final CheckTermsAgreementUseCase checkTermsAgreementUseCase;

    public SignupTermsController(
            GetSignupTermsUseCase getSignupTermsUseCase,
            CheckTermsAgreementUseCase checkTermsAgreementUseCase
    ) {
        this.getSignupTermsUseCase = getSignupTermsUseCase;
        this.checkTermsAgreementUseCase = checkTermsAgreementUseCase;
    }

    @GetMapping
    public ApiResponse<SignupTermsResponse> getTerms() {
        SignupTermsResponse response = new SignupTermsResponse(
                getSignupTermsUseCase.getSignupTerms()
                        .stream()
                        .map(SignupTermResponse::from)
                        .toList()
        );

        return ApiResponse.success(response);
    }

    @PostMapping("/check")
    public ApiResponse<TermsAuthTokenResponse> checkTerms(
            @Valid @RequestBody CheckTermsAgreementRequest request
    ) {
        TermsAgreementResult result =
                checkTermsAgreementUseCase.checkTermsAgreement(
                        request.toCommand()
                );

        return ApiResponse.success(
                TermsAuthTokenResponse.from(result)
        );
    }
}
