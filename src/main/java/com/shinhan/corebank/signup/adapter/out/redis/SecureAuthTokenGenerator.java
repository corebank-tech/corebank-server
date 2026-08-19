package com.shinhan.corebank.signup.adapter.out.redis;

import com.shinhan.corebank.signup.application.port.out.AuthTokenGeneratorPort;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

// 회원가입 인증 토큰을 256비트 CSPRNG 난수로 생성한다.
@Component
public class SecureAuthTokenGenerator implements AuthTokenGeneratorPort {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generateTermsAuthToken() {
        return generate("TERMS_AUTH_");
    }

    @Override
    public String generateUserIdCheckToken() {
        return generate("USER_ID_CHECK_");
    }

    @Override
    public String generateEmailVerificationToken() {
        return generate("EMAIL_VERIFICATION_");
    }

    // 모든 인증 완료 토큰은 동일한 256bit CSPRNG 정책을 적용한다.
    private String generate(String prefix) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);

        return prefix + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }
}
