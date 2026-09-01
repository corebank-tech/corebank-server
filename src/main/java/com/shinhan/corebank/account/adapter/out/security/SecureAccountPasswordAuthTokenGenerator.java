package com.shinhan.corebank.account.adapter.out.security;

import com.shinhan.corebank.account.application.port.out.AccountPasswordAuthTokenGeneratorPort;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

// 256bit CSPRNG 난수로 계좌비밀번호 인증 토큰을 생성한다.
@Component
public class SecureAccountPasswordAuthTokenGenerator implements AccountPasswordAuthTokenGeneratorPort {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
