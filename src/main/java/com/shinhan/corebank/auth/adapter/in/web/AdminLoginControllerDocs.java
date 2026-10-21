package com.shinhan.corebank.auth.adapter.in.web;

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
import org.springframework.web.bind.annotation.RequestBody;

/** 관리자 로그인 Swagger 명세. 컨트롤러에는 매핑만 남긴다. */
@Tag(name = "관리자 인증", description = "관리자 로그인 API")
public interface AdminLoginControllerDocs {

    @Operation(
            operationId = "adminLogin",
            summary = "관리자 로그인",
            description = "role=ADMIN 계정만 통과한다. 세션은 30분 무조작 만료이고 JSESSIONID·CSRF 쿠키는 고객 로그인과 같이 발급된다.")
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
                description = "`ATH0102` 로그인 비밀번호 오류 횟수 초과로 계정 잠금 · `ATH0106` 이용정지된 계정",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse);
}
