package com.shinhan.corebank.otp.application.port.in;

// OTP 검증 성공 후 최종 거래에 사용할 인증 토큰을 반환한다.
public record VerifyOtpResult(String otpAuthToken) {
}
