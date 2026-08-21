package com.shinhan.corebank.otp.application.port.in;

// 발급된 Mock OTP 요청 ID와 번호 및 유효시간을 반환한다.
public record IssueOtpResult(
        String otpRequestId,
        String otpCode,
        long expiresIn
) {
}
