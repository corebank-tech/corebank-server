package com.shinhan.corebank.signup.adapter.out.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecureAuthTokenGeneratorTest {

    private static final String PREFIX = "TERMS_AUTH_";

    private final SecureAuthTokenGenerator generator =
            new SecureAuthTokenGenerator();

    @Test
    @DisplayName("약관 인증 토큰은 계약된 접두어와 URL-safe 문자열로 생성된다")
    void generatesUrlSafeTokenWithPrefix() {
        String token = generator.generateTermsAuthToken();

        assertThat(token).startsWith(PREFIX);
        assertThat(token.substring(PREFIX.length()))
                .matches("[A-Za-z0-9_-]+");
    }

    @Test
    @DisplayName("약관 인증 토큰의 난수 영역은 256비트다")
    void generatesTwoHundredFiftySixBitRandomValue() {
        String token = generator.generateTermsAuthToken();

        byte[] randomBytes = Base64.getUrlDecoder()
                .decode(token.substring(PREFIX.length()));

        assertThat(randomBytes).hasSize(32);
    }

    @Test
    @DisplayName("반복 생성한 약관 인증 토큰은 서로 중복되지 않는다")
    void generatesDistinctTokens() {
        Set<String> tokens = new HashSet<>();

        for (int index = 0; index < 1_000; index++) {
            tokens.add(generator.generateTermsAuthToken());
        }

        assertThat(tokens).hasSize(1_000);
    }
}
