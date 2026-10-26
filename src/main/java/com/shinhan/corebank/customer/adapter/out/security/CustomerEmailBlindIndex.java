package com.shinhan.corebank.customer.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// 랜덤 nonce로 암호화된 이메일은 동등 비교할 수 없으므로 별도 HMAC 검색 키를 생성한다.
// 개인정보 암호화용 AES 키와 HMAC 키는 반드시 서로 다른 비밀값을 사용한다.
public final class CustomerEmailBlindIndex {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String DOMAIN_PREFIX = "corebank:customer:email:v1:";
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec key;

    public CustomerEmailBlindIndex(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("이메일 검색 해시 키는 32바이트여야 합니다.");
        }
        this.key = new SecretKeySpec(keyBytes.clone(), ALGORITHM);
    }

    // 기존 회원가입·연락처 변경과 동일하게 공백 제거 및 소문자 정규화 후 정확 일치용 해시를 계산한다.
    public String hash(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일 검색 해시 입력값이 비어 있습니다.");
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(
                    (DOMAIN_PREFIX + normalized).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("이메일 검색 해시 생성에 실패했습니다.", e);
        }
    }
}
