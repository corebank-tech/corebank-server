package com.shinhan.corebank.otp.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

// OTP 검증 실패 시 토큰 없이 오류 횟수와 잔여 횟수를 반환한다.
public record OtpFailureData(
        @Schema(description = "실패 응답에서는 null", nullable = true)
        String otpAuthToken,

        @Schema(description = "OTP 누적 오류 횟수", example = "2")
        int errorCount,

        @Schema(description = "잠금까지 남은 시도 횟수", example = "3")
        int remainingAttempts
) {
}
