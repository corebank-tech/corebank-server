package com.shinhan.corebank.otp.application.service;

import com.shinhan.corebank.otp.domain.model.OtpAttemptResult;

// DB 커밋 후 성공 토큰 반환 또는 실패 예외 변환에 사용할 검증 결과다.
public record OtpVerificationProcessResult(
        String otpAuthToken,
        OtpAttemptResult attemptResult
) {
    public static OtpVerificationProcessResult success(String otpAuthToken) {
        return new OtpVerificationProcessResult(otpAuthToken, null);
    }

    public static OtpVerificationProcessResult failure(OtpAttemptResult attemptResult) {
        return new OtpVerificationProcessResult(null, attemptResult);
    }

    public boolean success() {
        return otpAuthToken != null;
    }
}
