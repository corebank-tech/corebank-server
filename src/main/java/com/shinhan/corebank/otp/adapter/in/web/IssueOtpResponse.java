package com.shinhan.corebank.otp.adapter.in.web;

import com.shinhan.corebank.otp.application.port.in.IssueOtpResult;

// Phase 1 Mock OTP 번호와 요청 식별자 및 유효시간을 반환한다.
public record IssueOtpResponse(
        String otpRequestId,
        String otpCode,
        long expiresIn
) {
    public static IssueOtpResponse from(IssueOtpResult result) {
        return new IssueOtpResponse(
                result.otpRequestId(),
                result.otpCode(),
                result.expiresIn()
        );
    }
}
