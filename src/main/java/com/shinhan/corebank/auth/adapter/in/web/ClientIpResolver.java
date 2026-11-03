package com.shinhan.corebank.auth.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.stereotype.Component;

// 원격 주소에서 로그인 요청 IP를 추출. prod는 톰캣 RemoteIpValve가 ALB 뒤 접속 IP로 바꿔 둔다(#458)
@Component
public class ClientIpResolver {

    private static final int MAX_IP_LENGTH = 45;

    public String resolve(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        return validateAndNormalize(request.getRemoteAddr());
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
