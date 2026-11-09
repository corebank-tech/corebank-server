package com.shinhan.corebank.auth.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 미가입 아이디 실패 횟수를 아이디 원문 없이 찾기 위한 검색 해시. 같은 아이디는 항상 같은 값이 나와야 해서
// 랜덤 솔트를 쓰는 BCrypt 대신 비밀키 HMAC을 쓰고, 키는 외부 비밀값에서만 읽어 미설정이면 기동을 멈춘다.
@Component
public class LoginIdBlindIndex {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String DOMAIN_PREFIX = "corebank:auth:login-id:v1:";
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec key;

    public LoginIdBlindIndex(@Value("${APP_LOGIN_ID_HMAC_KEY_BASE64:}") String keyBase64) {
        final byte[] keyBytes = decodeKey(keyBase64);
        this.key = new SecretKeySpec(keyBytes, ALGORITHM);
        Arrays.fill(keyBytes, (byte) 0);
    }

    // 호출부가 소문자로 정규화한 아이디를 받는다. 정규화를 여기서 다시 하면 조회 아이디와 어긋날 수 있다.
    public String hash(String normalizedUserId) {
        try {
            final Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            final byte[] digest = mac.doFinal((DOMAIN_PREFIX + normalizedUserId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("로그인 아이디를 HMAC-SHA256으로 변환하지 못했습니다.", e);
        }
    }

    private static byte[] decodeKey(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("APP_LOGIN_ID_HMAC_KEY_BASE64 환경변수가 없습니다.");
        }

        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            // 키 원문이나 Base64 문자열을 예외·로그에 포함하지 않는다.
            throw new IllegalStateException("APP_LOGIN_ID_HMAC_KEY_BASE64 값이 Base64 형식이 아닙니다.");
        }
        if (bytes.length != KEY_BYTES) {
            Arrays.fill(bytes, (byte) 0);
            throw new IllegalStateException("APP_LOGIN_ID_HMAC_KEY_BASE64 값은 Base64로 디코딩해 32바이트여야 합니다.");
        }
        return bytes;
    }
}
