package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.adapter.in.security.CsrfTokenCookieIssuer;
import com.shinhan.corebank.auth.adapter.in.security.SessionLoginManager;
import com.shinhan.corebank.auth.application.port.in.LoginCommand;
import com.shinhan.corebank.auth.application.port.in.LoginResult;
import com.shinhan.corebank.auth.application.port.in.LoginUseCase;
import com.shinhan.corebank.common.response.ApiResponse;
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
public class LoginController {

    private final LoginUseCase loginUseCase;
    private final ClientIpResolver clientIpResolver;
    private final SessionLoginManager sessionLoginManager;
    private final CsrfTokenCookieIssuer csrfTokenCookieIssuer;

    @PostMapping("/login")
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
