package com.shinhan.corebank.otp.adapter.out.security;

import com.shinhan.corebank.otp.application.port.out.OtpTokenGeneratorPort;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

// OTP 요청 ID와 최소 256bit 인증 완료 토큰을 예측 불가능하게 생성한다.
@Component
public class SecureOtpTokenGenerator implements OtpTokenGeneratorPort {

    private static final int RANDOM_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generateRequestId() {
        return generate("OTP_REQ_");
    }

    @Override
    public String generateAuthToken() {
        return generate("OTP_AUTH_");
    }

    private String generate(String prefix) {
        byte[] bytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
