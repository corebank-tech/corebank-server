package com.shinhan.corebank.signup.adapter.out.redis;

import com.shinhan.corebank.signup.application.port.out.VerificationRequestIdGeneratorPort;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

// 이메일 인증 요청 ID를 256bit 난수로 생성한다.
@Component
public class SecureVerificationRequestIdGenerator implements VerificationRequestIdGeneratorPort {

    private static final int ID_BYTES = 32;
    private static final String PREFIX = "EVF_";

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generateEmailVerificationId() {
        byte[] randomBytes = new byte[ID_BYTES];
        secureRandom.nextBytes(randomBytes);

        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
