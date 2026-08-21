package com.shinhan.corebank.otp.adapter.in.web;

import com.shinhan.corebank.otp.application.port.in.VerifyOtpResult;

// OTP 검증 성공 시 토큰과 null 오류 횟수 필드를 상세명세 형식으로 반환한다.
public record VerifyOtpResponse(
        String otpAuthToken,
        Integer errorCount,
        Integer remainingAttempts
) {
    public static VerifyOtpResponse success(VerifyOtpResult result) {
        return new VerifyOtpResponse(result.otpAuthToken(), null, null);
    }
}
