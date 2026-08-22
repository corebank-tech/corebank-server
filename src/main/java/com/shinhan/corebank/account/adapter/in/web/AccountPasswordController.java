package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.VerifyAccountPasswordCommand;
import com.shinhan.corebank.account.application.port.in.VerifyAccountPasswordResult;
import com.shinhan.corebank.account.application.port.in.VerifyAccountPasswordUseCase;
import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 계좌비밀번호 검증 API를 계좌 하위 경로로 제공한다.
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Validated
@Tag(
        name = "계좌",
        description = "계좌 조회 및 계좌비밀번호 인증 API"
)
public class AccountPasswordController {

    private static final String SUCCESS_MESSAGE =
            "계좌비밀번호 검증이 완료되었습니다.";

    private final VerifyAccountPasswordUseCase verifyUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;

    @PostMapping("/{accountId}/password/verify")
    @Operation(
            summary = "계좌비밀번호 검증",
            description = "로그인 고객이 소유한 계좌의 숫자 4자리 비밀번호를 검증하고 300초 일회용 인증 토큰을 발급한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "계좌비밀번호 검증 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "`CMN0001` 요청 형식 오류 · `CMN0002` 필수값 누락 · `APW0001` 비밀번호 불일치",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "`APW0101` 계좌비밀번호 5회 오류로 잠김",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "`ACC0201` 계좌를 찾을 수 없거나 접근할 수 없음",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    public ApiResponse<AccountPasswordVerifyResponse> verify(
            @Parameter(
                    description = "계좌비밀번호를 검증할 계좌의 내부 식별자",
                    required = true,
                    example = "101"
            )
            @PathVariable
            @Positive
            Long accountId,

            @RequestBody
            AccountPasswordVerifyRequest request
    ) {
        Long customerId =
                currentCustomerProvider.getCurrentCustomerId();

        VerifyAccountPasswordResult result =
                verifyUseCase.verify(
                        new VerifyAccountPasswordCommand(
                                customerId,
                                accountId,
                                request.accountPassword()
                        )
                );

        return ApiResponse.success(
                AccountPasswordVerifyResponse.from(result),
                SUCCESS_MESSAGE
        );
    }
}
