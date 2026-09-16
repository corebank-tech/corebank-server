package com.shinhan.corebank.auth.application.port.in;

import java.util.Objects;

// 로그인 검증에 필요한 아이디, 비밀번호와 요청 IP
public record LoginCommand(String userId, String password, String requestIp) {

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
        return "LoginCommand[" + "userId=" + userId + ", password=[PROTECTED]" + ", requestIp=" + requestIp + ']';
    }
}
