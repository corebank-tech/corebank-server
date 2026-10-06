package com.shinhan.corebank.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

// SecurityConfig가 addFilterBefore로 직접 등록한다 - @Component로 중복 등록하지 않는다
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ID_KEY = "correlationId";
    // 16진수엔 숫자(0-9)가 포함되므로 UUID 형식이어도 세그먼트 안에 계좌번호·전화번호를 그대로
    // 심을 수 있다 - 형식 검증과 별도로 11자리 이상 연속 숫자 여부까지 봐야 한다(PR #560 3차 리뷰)
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("\\d{11,}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        while (correlationId == null || !isSafe(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        response.setHeader(HEADER, correlationId);
        MDC.put(CORRELATION_ID_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_KEY);
        }
    }

    private static boolean isSafe(String value) {
        return UUID_PATTERN.matcher(value).matches()
                && !LONG_DIGIT_RUN.matcher(value).find();
    }
}
