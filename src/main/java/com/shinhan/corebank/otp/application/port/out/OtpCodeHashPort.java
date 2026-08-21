package com.shinhan.corebank.otp.application.port.out;

// OTP 평문을 단방향 해시하고 입력값과 비교한다.
public interface OtpCodeHashPort {
    String hash(String otpCode);
    boolean matches(String otpCode, String otpCodeHash);
}
