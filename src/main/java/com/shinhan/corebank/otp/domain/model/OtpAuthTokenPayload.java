package com.shinhan.corebank.otp.domain.model;

import java.util.Objects;

// Redis OTP 인증 토큰을 검증 요청과 로그인 고객에 연결한다.
public record OtpAuthTokenPayload(String otpRequestId, Long customerId) {
    public OtpAuthTokenPayload {
        Objects.requireNonNull(otpRequestId);
        Objects.requireNonNull(customerId);
    }
}
