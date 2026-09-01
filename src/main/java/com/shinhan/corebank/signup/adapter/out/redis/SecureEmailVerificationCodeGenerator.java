package com.shinhan.corebank.signup.adapter.out.redis;

import com.shinhan.corebank.signup.application.port.out.EmailVerificationCodeGeneratorPort;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

// 앞자리 0을 포함할 수 있는 숫자 6자리 인증번호를 생성한다.
@Component
public class SecureEmailVerificationCodeGenerator implements EmailVerificationCodeGeneratorPort {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }
}
