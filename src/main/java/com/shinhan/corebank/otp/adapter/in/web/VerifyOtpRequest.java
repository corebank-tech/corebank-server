package com.shinhan.corebank.otp.adapter.in.web;

import com.shinhan.corebank.otp.application.port.in.VerifyOtpCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// OTP 요청 ID와 사용자가 입력한 숫자 6자리 OTP를 입력받는다.
public record VerifyOtpRequest(
        @NotBlank String otpRequestId,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String otpCode
) {
    public VerifyOtpCommand toCommand(Long customerId) {
        return new VerifyOtpCommand(customerId, otpRequestId, otpCode);
    }
}
