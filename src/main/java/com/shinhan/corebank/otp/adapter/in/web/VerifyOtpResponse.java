package com.shinhan.corebank.otp.adapter.in.web;

import com.shinhan.corebank.otp.application.port.in.VerifyOtpResult;
import io.swagger.v3.oas.annotations.media.Schema;

// OTP 검증 성공 시 토큰과 null 오류 횟수 필드를 상세명세 형식으로 반환한다.
public record VerifyOtpResponse(
        @Schema(
                description = "최종 거래에서 한 번 사용할 수 있는 OTP 인증 토큰",
                example = "OTP_AUTH_7xP9qK2RmY5vLw8ZbC6dE4fG1hH0jM3n"
        )
        String otpAuthToken,

        @Schema(
                description = "성공 응답에서는 null",
                nullable = true
        )
        Integer errorCount,

        @Schema(
                description = "성공 응답에서는 null",
                nullable = true
        )
        Integer remainingAttempts
) {
    public static VerifyOtpResponse success(VerifyOtpResult result) {
        return new VerifyOtpResponse(result.otpAuthToken(), null, null);
    }
}
