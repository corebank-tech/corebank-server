package com.shinhan.corebank.signup.api;

// 다른 모듈에 이메일 변경 인증 토큰의 검증·소비 기능만 공개한다.
public interface EmailChangeVerificationTokenVerifier {

    void verifyAndConsumeForEmailChange(
            String emailVerificationToken,
            String email
    );
}
