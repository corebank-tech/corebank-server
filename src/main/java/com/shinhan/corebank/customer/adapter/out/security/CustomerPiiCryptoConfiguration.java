package com.shinhan.corebank.customer.adapter.out.security;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 암호화 키는 외부 비밀값에서만 읽고, 미설정/오설정 시 임시 키로 대체하지 않는다.
// 기존 customer 테이블은 평문이므로 데이터 전환이 끝나기 전에는 기본값인 비활성 상태를 유지한다.
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.pii", name = "enabled", havingValue = "true")
public class CustomerPiiCryptoConfiguration {

    private static final int KEY_BYTES = 32;

    @Bean
    CustomerPiiCrypto customerPiiCrypto(
            @Value("${APP_PII_AES_KEY_BASE64:}") String aesKeyBase64,
            @Value("${APP_PII_HMAC_KEY_BASE64:}") String hmacKeyBase64) {
        byte[] aesKey = decodeKey(aesKeyBase64);
        byte[] hmacKey = null;
        try {
            hmacKey = decodeKey(hmacKeyBase64);
            if (MessageDigest.isEqual(aesKey, hmacKey)) {
                throw new IllegalStateException("개인정보 암호화 키와 검색 해시 키는 서로 달라야 합니다.");
            }
            return new CustomerPiiCrypto(new CustomerPiiCipher(aesKey), new CustomerEmailBlindIndex(hmacKey));
        } finally {
            Arrays.fill(aesKey, (byte) 0);
            if (hmacKey != null) {
                Arrays.fill(hmacKey, (byte) 0);
            }
        }
    }

    private static byte[] decodeKey(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("개인정보 암호화 키 설정이 누락되었습니다.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            // 키 원문이나 Base64 문자열을 예외·로그에 포함하지 않는다.
            throw new IllegalStateException("개인정보 암호화 키 형식이 올바르지 않습니다.");
        }
        if (bytes.length != KEY_BYTES) {
            Arrays.fill(bytes, (byte) 0);
            throw new IllegalStateException("개인정보 암호화 키는 32바이트여야 합니다.");
        }
        return bytes;
    }
}
