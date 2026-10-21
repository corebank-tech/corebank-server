package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.customer.api.CustomerRole;
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

// 로그인 검증 후 Spring Security 인증 세션을 생성하는 Controller
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "로그인과 세션 인증 API")
public class LoginController {

    private final LoginSessionIssuer loginSessionIssuer;

    @PostMapping("/login")
    @Operation(
            operationId = "login",
            summary = "로그인",
            description = "아이디와 비밀번호를 검증하고 JSESSIONID 세션과 CSRF 쿠키를 발급한다. "
                    + "세션은 공용 MySQL에 저장되어 다른 WAS에서도 유지된다. 고객은 10분 무조작 시 만료된다. "
                    + "Swagger UI에서는 로그인 후 발급된 쿠키와 X-XSRF-TOKEN을 사용한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0001` 요청 형식 오류 또는 필수 입력값 누락",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`ATH0101` 아이디 또는 비밀번호 불일치",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`ATH0102` 로그인 비밀번호 오류 횟수 초과로 계정 잠금 · `ATH0106` 관리자 이용정지 계정(비밀번호가 일치할 때만 반환)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "`CMN9999` 서버 오류 — 로그인이 완료되지 않음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        // 관리자는 이 문으로 들어오지 못한다. 역할을 가리지 않으면 /admin/auth/login 의 응답 통일이
        // 의미를 잃는다 — 이 경로로 로그인해 GET /customers/me 를 보면 관리자인지 알 수 있기 때문이다.
        return ApiResponse.success(loginSessionIssuer.authenticateAndIssueSession(
                loginRequest, CustomerRole.CUSTOMER, servletRequest, servletResponse));
    }
}
