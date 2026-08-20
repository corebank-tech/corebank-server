package com.shinhan.corebank.signup.application.port.in;

// 사용 가능한 아이디와 중복확인 완료 토큰을 반환한다.
public record CheckUserIdResult(
        boolean isAvailable,
        String userIdCheckToken,
        long expiresIn
) {
}
