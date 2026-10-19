package com.shinhan.infoetl.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class Pseudonymizer {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKeySpec key;

    public Pseudonymizer(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException("HMAC 키는 최소 " + MIN_KEY_BYTES + "바이트여야 합니다.");
        }
        this.key = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    // 종류 접두어를 붙여 계좌 5번과 고객 5번이 같은 키가 되지 않게 한다. 같은 키·ID면 항상 같은 64자리 hex다.
    public String accountKey(long accountId) {
        return hmacHex("account:" + accountId);
    }

    public String customerKey(long customerId) {
        return hmacHex("customer:" + customerId);
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
