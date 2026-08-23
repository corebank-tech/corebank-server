package com.shinhan.corebank.otp.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shinhan.corebank.otp.application.port.in.IssueOtpResult;
import io.swagger.v3.oas.annotations.media.Schema;

// Phase 1 Mock OTP 번호와 요청 식별자 및 유효시간을 반환한다.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IssueOtpResponse(
        @Schema(
                description = "OTP 발급 요청 식별자",
                example = "OTP_REQ_7xP9qK2RmY5vLw8Z"
        )
        String otpRequestId,

        @Schema(
                description = "Phase 1 Mock OTP 번호. 운영 설정에서는 응답에서 제외된다.",
                example = "123456",
                pattern = "^\\d{6}$",
                nullable = true
        )
        String otpCode,

        @Schema(description = "OTP 남은 유효시간(초)", example = "180")
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
