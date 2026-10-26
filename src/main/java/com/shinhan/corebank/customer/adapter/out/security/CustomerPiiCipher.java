package com.shinhan.corebank.customer.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// 고객 연락처의 DB 저장값 암복호화. JPA Converter 및 JDBC 백필에서 같은 형식을 사용한다.
// v1:<Base64URL(12바이트 nonce || 암호문 || 16바이트 인증 태그)>
// 키 주입 및 JPA 연동은 별도 단계에서 구현하며, 이 클래스는 자체적으로 키를 생성하지 않는다.
public final class CustomerPiiCipher {

    private static final String VERSION_PREFIX = "v1:";
    private static final byte[] VERSION_AAD = VERSION_PREFIX.getBytes(StandardCharsets.US_ASCII);
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / Byte.SIZE;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public CustomerPiiCipher(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("개인정보 암호화 키는 32바이트여야 합니다.");
        }
        this.key = new SecretKeySpec(keyBytes.clone(), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }

        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = initCipher(Cipher.ENCRYPT_MODE, nonce);
            byte[] ciphertextAndTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = Arrays.copyOf(nonce, nonce.length + ciphertextAndTag.length);
            System.arraycopy(ciphertextAndTag, 0, payload, nonce.length, ciphertextAndTag.length);
            return VERSION_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("개인정보 암호화에 실패했습니다.", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        if (!encoded.startsWith(VERSION_PREFIX)) {
            throw new IllegalArgumentException("지원하지 않는 개인정보 암호문 형식입니다.");
        }

        byte[] payload;
        try {
            payload = Base64.getUrlDecoder().decode(encoded.substring(VERSION_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("개인정보 암호문 형식이 올바르지 않습니다.", e);
        }
        if (payload.length < NONCE_BYTES + TAG_BYTES) {
            throw new IllegalArgumentException("개인정보 암호문 길이가 올바르지 않습니다.");
        }

        byte[] nonce = Arrays.copyOf(payload, NONCE_BYTES);
        byte[] ciphertextAndTag = Arrays.copyOfRange(payload, NONCE_BYTES, payload.length);
        try {
            Cipher cipher = initCipher(Cipher.DECRYPT_MODE, nonce);
            return new String(cipher.doFinal(ciphertextAndTag), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // 원문/키/암호문을 오류 메시지에 포함하지 않는다.
            throw new IllegalStateException("개인정보 복호화에 실패했습니다.", e);
        }
    }

    private Cipher initCipher(int mode, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(VERSION_AAD);
        return cipher;
    }
}
