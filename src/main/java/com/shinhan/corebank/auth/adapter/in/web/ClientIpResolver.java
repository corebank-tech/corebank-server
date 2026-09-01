package com.shinhan.corebank.auth.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.stereotype.Component;

// ALB 전달 헤더 또는 원격 주소에서 로그인 요청 IP를 추출
@Component
public class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final int MAX_IP_LENGTH = 45;

    public String resolve(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        String clientIp = hasText(forwardedFor) ? lastForwardedIp(forwardedFor) : request.getRemoteAddr();

        return validateAndNormalize(clientIp);
    }

    // 단일 신뢰 프록시인 ALB가 직접 확인해 마지막에 추가한 접속 IP를 사용
    private String lastForwardedIp(String forwardedFor) {
        int lastSeparatorIndex = forwardedFor.lastIndexOf(',');
        return forwardedFor.substring(lastSeparatorIndex + 1).trim();
    }

    // customer와 audit_log 컬럼 제약에 맞는 IP만 허용
    private String validateAndNormalize(String clientIp) {
        if (!hasText(clientIp)) {
            throw new IllegalArgumentException("로그인 IP는 필수입니다.");
        }

        String normalizedIp = clientIp.trim();

        if (normalizedIp.length() > MAX_IP_LENGTH) {
            throw new IllegalArgumentException("로그인 IP는 45자 이하여야 합니다.");
        }

        return normalizedIp;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
