package com.shinhan.corebank.otp.adapter.out.security;

import com.shinhan.corebank.otp.application.port.out.OtpCodeGeneratorPort;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

// 앞자리 0을 포함할 수 있는 숫자 6자리 Mock OTP를 생성한다.
@Component
public class SecureOtpCodeGenerator implements OtpCodeGeneratorPort {

    private static final int OTP_BOUND = 1_000_000;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        return "%06d".formatted(secureRandom.nextInt(OTP_BOUND));
    }
}
