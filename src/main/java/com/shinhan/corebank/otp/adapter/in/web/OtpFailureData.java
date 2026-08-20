package com.shinhan.corebank.otp.adapter.in.web;

// OTP 검증 실패 시 토큰 없이 오류 횟수와 잔여 횟수를 반환한다.
public record OtpFailureData(
        String otpAuthToken,
        int errorCount,
        int remainingAttempts
) {
}
