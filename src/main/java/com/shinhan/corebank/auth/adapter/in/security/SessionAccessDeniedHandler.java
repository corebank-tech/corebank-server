package com.shinhan.corebank.auth.adapter.in.security;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// 인증된 사용자가 접근 권한이 없는 자원을 요청하면 공통 403 응답으로 변환
@Component
public class SessionAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SessionAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        CommonErrorCode errorCode = CommonErrorCode.FORBIDDEN;

        response.setStatus(errorCode.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getOutputStream(), new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), null));
    }
}
