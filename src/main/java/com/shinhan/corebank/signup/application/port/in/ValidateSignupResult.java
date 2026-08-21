package com.shinhan.corebank.signup.application.port.in;

// 회원가입 입력 검증 후 발급한 임시 가입 토큰을 반환한다.
public record ValidateSignupResult(
        String tempSignupToken,
        long expiresIn
) {
}
