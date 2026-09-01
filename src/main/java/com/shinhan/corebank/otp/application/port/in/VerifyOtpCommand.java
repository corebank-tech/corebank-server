package com.shinhan.corebank.otp.application.port.in;

// 로그인 고객이 입력한 OTP 요청 ID와 숫자 6자리를 전달한다.
public record VerifyOtpCommand(Long customerId, String otpRequestId, String otpCode) {}
