package com.shinhan.corebank.signup.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.signup.adapter.in.web.dto.CheckTermsAgreementRequest;
import com.shinhan.corebank.signup.adapter.in.web.dto.SignupTermResponse;
import com.shinhan.corebank.signup.adapter.in.web.dto.SignupTermsResponse;
import com.shinhan.corebank.signup.adapter.in.web.dto.TermsAuthTokenResponse;
import com.shinhan.corebank.signup.application.port.in.CheckTermsAgreementUseCase;
import com.shinhan.corebank.signup.application.port.in.GetSignupTermsUseCase;
import com.shinhan.corebank.signup.application.port.in.TermsAgreementResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 회원가입 약관 조회와 동의 검증 HTTP API를 제공한다.
@RestController
@RequestMapping("/auth/terms")
@Tag(
        name = "약관",
        description = "회원가입 약관 조회·동의 검증 API"
)
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
    @Operation(
            summary = "회원가입 약관 조회",
            description = "현재 적용 중인 회원가입 약관의 버전·내용·필수 여부·열람 필요 여부를 조회한다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "회원가입 약관 조회 성공"
    )
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
    @Operation(
            summary = "회원가입 약관 동의 검증",
            description = "약관 버전·열람·동의 상태를 검증하고 termsAuthToken을 발급한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "약관 동의 검증 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "`CMN0001` 요청 형식 또는 약관 정보 오류 · `ATH0006` 필수 약관 미동의",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
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
