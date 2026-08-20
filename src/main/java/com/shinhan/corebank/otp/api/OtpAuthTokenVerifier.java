package com.shinhan.corebank.otp.api;

// 다른 업무 모듈에 OTP 인증 토큰의 검증과 일회성 소비 기능을 공개한다.
public interface OtpAuthTokenVerifier {
    void verifyAndConsume(OtpAuthTokenVerification verification);
}
