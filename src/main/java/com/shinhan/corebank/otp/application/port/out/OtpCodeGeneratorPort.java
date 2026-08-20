package com.shinhan.corebank.otp.application.port.out;

// 화면에 표시할 숫자 6자리 Mock OTP를 생성한다.
public interface OtpCodeGeneratorPort {
    String generate();
}
