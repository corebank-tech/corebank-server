package com.shinhan.corebank.customer.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.customer.application.port.in.LoginStatusQueryUseCase;
import com.shinhan.corebank.customer.application.port.in.LoginStatusResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(
        name = "고객",
        description = "로그인 고객의 기본정보 조회·변경 API"
)
public class LoginStatusController {
    private final LoginStatusQueryUseCase loginStatusQueryUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;

    @GetMapping("/login-status")
    @Operation(
            operationId = "getLoginStatus",
            summary = "로그인 상태 조회",
            description = "이전 로그인 일시, 현재 접속 IP와 최근 거래 일시를 조회한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "로그인 상태 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ApiResponse<LoginStatusResponse> getLoginStatus() {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        LoginStatusResult result = loginStatusQueryUseCase.getLoginStatus(customerId);

        return ApiResponse.success(LoginStatusResponse.from(result));
    }
}
