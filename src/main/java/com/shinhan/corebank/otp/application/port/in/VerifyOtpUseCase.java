package com.shinhan.corebank.otp.application.port.in;

// OTP 번호를 검증하고 인증 완료 토큰을 발급하는 인바운드 유스케이스다.
public interface VerifyOtpUseCase {
    VerifyOtpResult verify(VerifyOtpCommand command);
}
