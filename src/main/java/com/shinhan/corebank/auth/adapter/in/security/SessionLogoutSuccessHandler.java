package com.shinhan.corebank.auth.adapter.in.security;

import com.shinhan.corebank.common.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// 로그아웃 결과를 공통 응답으로 변환하고 보호 화면의 브라우저 캐시를 방지한다.
@Component
public class SessionLogoutSuccessHandler implements LogoutSuccessHandler {

    private static final String LOGOUT_SUCCESS_MESSAGE = "로그아웃되었습니다.";

    private final ObjectMapper objectMapper;
    private final SessionAuthenticationEntryPoint authenticationEntryPoint;

    public SessionLogoutSuccessHandler(
            ObjectMapper objectMapper, SessionAuthenticationEntryPoint authenticationEntryPoint) {
        this.objectMapper = objectMapper;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        preventCaching(response);

        if (authentication == null) {
            authenticationEntryPoint.commence(
                    request, response, new InsufficientAuthenticationException("로그아웃할 인증 세션이 존재하지 않습니다."));
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.<Void>success(null, LOGOUT_SUCCESS_MESSAGE));
    }

    private void preventCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }
}
