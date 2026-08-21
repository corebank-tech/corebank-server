package com.shinhan.corebank.signup.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.signup.adapter.in.web.dto.SignupConfirmationResponse;
import com.shinhan.corebank.signup.adapter.in.web.dto.ValidateSignupRequest;
import com.shinhan.corebank.signup.adapter.in.web.dto.ValidateSignupResponse;
import com.shinhan.corebank.signup.application.port.in.GetSignupConfirmationUseCase;
import com.shinhan.corebank.signup.application.port.in.ValidateSignupUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 회원가입 입력 검증과 확인정보 조회 HTTP API를 제공한다.
@Tag(name = "회원가입 진행")
@RestController
@RequestMapping("/auth/signup")
public class SignupProgressController {

    private final ValidateSignupUseCase validateSignupUseCase;
    private final GetSignupConfirmationUseCase getSignupConfirmationUseCase;

    public SignupProgressController(
            ValidateSignupUseCase validateSignupUseCase,
            GetSignupConfirmationUseCase getSignupConfirmationUseCase
    ) {
        this.validateSignupUseCase = validateSignupUseCase;
        this.getSignupConfirmationUseCase = getSignupConfirmationUseCase;
    }

    @Operation(
            summary = "회원가입 입력정보 검증",
            description = """
                    최초 요청은 네 인증 토큰을 검증·소비하고 tempSignupToken을 발급한다. \
                    정보수정 요청은 기존 임시 토큰을 전달하며 아이디 또는 이메일 변경 시에만 새 인증 토큰이 필요하다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "입력 검증 성공 및 tempSignupToken 발급"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "CMN0001 입력·임시토큰 오류, ATH0001·ATH0002·ATH0004·ATH0005 입력 검증 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "ATH0103 이메일 토큰, ATH0104 약관 토큰, ATH0105 계좌 토큰 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "ATH0301 아이디 중복, ATH0302 이메일 중복",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/validate")
    public ApiResponse<ValidateSignupResponse> validate(
            @Valid @RequestBody ValidateSignupRequest request
    ) {
        return ApiResponse.success(ValidateSignupResponse.from(
                validateSignupUseCase.validate(request.toCommand())
        ));
    }

    @Operation(
            summary = "회원가입 확인정보 조회",
            description = "tempSignupToken을 소비하지 않고 마스킹된 확인정보를 조회한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "마스킹된 확인정보 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "CMN0001 X-Signup-Token 누락·무효·만료",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/confirm-info")
    public ApiResponse<SignupConfirmationResponse> getConfirmation(
            @Parameter(
                    description = "회원가입 입력 검증 후 발급된 임시 가입 토큰",
                    required = true,
                    example = "TEMP_SIGNUP_AbCdEf123456"
            )
            @RequestHeader(value = "X-Signup-Token", required = false)
            String tempSignupToken
    ) {
        return ApiResponse.success(SignupConfirmationResponse.from(
                getSignupConfirmationUseCase.getConfirmation(tempSignupToken)
        ));
    }
}
