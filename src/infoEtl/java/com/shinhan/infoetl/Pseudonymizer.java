package com.shinhan.infoetl;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class Pseudonymizer {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public Pseudonymizer(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length == 0) {
            throw new IllegalArgumentException("HMAC 키가 비어 있습니다.");
        }
        this.key = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    // ID를 10진수 문자열로 바꿔 HMAC을 건다. 같은 키·ID면 항상 같은 64자리 hex라 재적재해도 키 값이 같다.
    public String pseudonymize(long id) {
        return hmacHex(Long.toString(id));
    }

    String hmacHex(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC 계산에 실패했습니다.", e);
        }
    }
}
