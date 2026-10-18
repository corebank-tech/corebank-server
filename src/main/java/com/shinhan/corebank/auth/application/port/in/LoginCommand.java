package com.shinhan.corebank.auth.application.port.in;

import com.shinhan.corebank.customer.api.CustomerRole;
import java.util.Objects;

// 로그인 검증에 필요한 아이디, 비밀번호와 요청 IP.
// requiredRole 은 그 경로가 요구하는 계정 구분이다 — null 이면 역할을 가리지 않는다(고객 로그인).
public record LoginCommand(String userId, String password, String requestIp, CustomerRole requiredRole) {

    private static final int MAX_REQUEST_IP_LENGTH = 45;

    public LoginCommand {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(requestIp, "requestIp must not be null");

        if (requestIp.isBlank()) {
            throw new IllegalArgumentException("requestIp must not be blank");
        }

        if (requestIp.length() > MAX_REQUEST_IP_LENGTH) {
            throw new IllegalArgumentException("requestIp must not exceed 45 characters");
        }
    }

    // 비밀번호가 로그에 노출되지 않도록 문자열 표현을 제한
    @Override
    public String toString() {
        return "LoginCommand[" + "userId=" + userId + ", password=[PROTECTED]" + ", requestIp=" + requestIp
                + ", requiredRole=" + requiredRole + ']';
    }
}
