package com.shinhan.corebank.otp.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shinhan.corebank.otp.application.port.in.IssueOtpResult;

// Phase 1 Mock OTP 번호와 요청 식별자 및 유효시간을 반환한다.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IssueOtpResponse(
        String otpRequestId,
        String otpCode,
        long expiresIn
) {
    public static IssueOtpResponse from(
            IssueOtpResult result,
            boolean exposeCode
    ) {
        return new IssueOtpResponse(
                result.otpRequestId(),
                exposeCode ? result.otpCode() : null,
                result.expiresIn()
        );
    }
}
