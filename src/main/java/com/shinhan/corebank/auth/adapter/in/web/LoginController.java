package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.adapter.in.security.CsrfTokenCookieIssuer;
import com.shinhan.corebank.auth.adapter.in.security.SessionLoginManager;
import com.shinhan.corebank.auth.application.port.in.LoginCommand;
import com.shinhan.corebank.auth.application.port.in.LoginResult;
import com.shinhan.corebank.auth.application.port.in.LoginUseCase;
import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

// 로그인 검증 후 Spring Security 인증 세션을 생성하는 Controller
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(
        name = "인증",
        description = "로그인과 세션 인증 API"
)
public class LoginController {

    private final LoginUseCase loginUseCase;
    private final ClientIpResolver clientIpResolver;
    private final SessionLoginManager sessionLoginManager;
    private final CsrfTokenCookieIssuer csrfTokenCookieIssuer;

    @PostMapping("/login")
    @Operation(
            operationId = "login",
            summary = "로그인",
            description = "아이디와 비밀번호를 검증하고 JSESSIONID 세션과 CSRF 쿠키를 발급한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "`CMN0001` 요청 형식 오류 또는 필수 입력값 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "`ATH0101` 아이디 또는 비밀번호 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "`ATH0102` 로그인 비밀번호 오류 횟수 초과로 계정 잠금",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        String requestIp =
                clientIpResolver.resolve(servletRequest);

        LoginResult loginResult = loginUseCase.login(
                new LoginCommand(
                        loginRequest.userId(),
                        loginRequest.password(),
                        requestIp
                )
        );

        OffsetDateTime sessionExpiresAt =
                sessionLoginManager.establishSession(
                        loginResult,
                        servletRequest,
                        servletResponse
                );

        csrfTokenCookieIssuer.rotate(
                servletRequest,
                servletResponse
        );

        LoginResponse response = new LoginResponse(
                loginResult.customerId(),
                loginResult.userName(),
                sessionExpiresAt
        );

        return ApiResponse.success(response);
    }
}
