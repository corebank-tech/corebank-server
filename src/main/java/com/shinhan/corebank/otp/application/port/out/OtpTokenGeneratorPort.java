package com.shinhan.corebank.otp.application.port.out;

// OTP 요청 ID와 인증 완료 토큰을 CSPRNG로 생성한다.
public interface OtpTokenGeneratorPort {
    String generateRequestId();
    String generateAuthToken();
}
