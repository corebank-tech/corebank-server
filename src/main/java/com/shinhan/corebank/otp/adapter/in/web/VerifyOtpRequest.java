package com.shinhan.corebank.otp.adapter.in.web;

import com.shinhan.corebank.otp.application.port.in.VerifyOtpCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// OTP 요청 ID와 사용자가 입력한 숫자 6자리 OTP를 입력받는다.
public record VerifyOtpRequest(
        @Schema(
                description = "OTP 발급 요청 식별자",
                example = "OTP_REQ_7xP9qK2RmY5vLw8Z"
        )
        @NotBlank String otpRequestId,

        @Schema(
                description = "사용자가 입력한 숫자 6자리 OTP",
                example = "123456",
                pattern = "^\\d{6}$",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        @NotBlank @Pattern(regexp = "^\\d{6}$") String otpCode
) {
    public VerifyOtpCommand toCommand(Long customerId) {
        return new VerifyOtpCommand(customerId, otpRequestId, otpCode);
    }
}
