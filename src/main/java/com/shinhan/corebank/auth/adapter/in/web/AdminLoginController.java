package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.customer.api.CustomerRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 관리자 전용 로그인 경로. 흐름은 고객 로그인과 같고 요구하는 역할만 다르다.
// 로그아웃은 고객과 같은 POST /auth/logout 을 쓴다 (admin_auth.md §5).
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminLoginController implements AdminLoginControllerDocs {

    private final LoginSessionIssuer loginSessionIssuer;

    @Override
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        return ApiResponse.success(loginSessionIssuer.authenticateAndIssueSession(
                loginRequest, CustomerRole.ADMIN, servletRequest, servletResponse));
    }
}
