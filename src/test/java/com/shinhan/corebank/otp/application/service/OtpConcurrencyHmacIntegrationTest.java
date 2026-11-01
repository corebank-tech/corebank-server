package com.shinhan.corebank.otp.application.service;

import java.util.Arrays;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// 해시 구현을 HMAC으로 바꿔도 같은 동시성 계약이 성립하는지 같은 시나리오로 다시 검증한다.
class OtpConcurrencyHmacIntegrationTest extends OtpConcurrencyIntegrationTest {

    @DynamicPropertySource
    static void useHmacCodeHash(DynamicPropertyRegistry registry) {
        registry.add("app.otp.code-hash.algorithm", () -> "hmac");
        registry.add("APP_OTP_CODE_HASH_KEY_BASE64", OtpConcurrencyHmacIntegrationTest::encodedTestKey);
    }

    // 키 문자열을 소스에 박지 않고 길이만 맞춰 만든다.
    private static String encodedTestKey() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 1);
        return Base64.getEncoder().encodeToString(key);
    }
}
