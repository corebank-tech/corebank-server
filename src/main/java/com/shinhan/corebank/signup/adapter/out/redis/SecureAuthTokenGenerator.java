package com.shinhan.corebank.signup.adapter.out.redis;

import com.shinhan.corebank.signup.application.port.out.AuthTokenGeneratorPort;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecureAuthTokenGenerator implements AuthTokenGeneratorPort {

    private static final int TOKEN_BYTES = 32;
    private static final String PREFIX = "TERMS_AUTH_";

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generateTermsAuthToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);

        return PREFIX + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }
}
