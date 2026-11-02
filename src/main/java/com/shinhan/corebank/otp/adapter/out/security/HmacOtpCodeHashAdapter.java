package com.shinhan.corebank.otp.adapter.out.security;

import com.shinhan.corebank.otp.application.port.out.OtpCodeHashPort;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// OTP 번호는 TTL과 5회 실패 잠금이 무차별 대입을 막으므로 느리게 설계된 BCrypt가 필요하지 않다.
// 비밀키를 쓰는 HMAC은 DB만 유출돼도 후보 해시를 계산할 수 없어 키 없는 BCrypt보다 유리하다.
// 키는 외부 비밀값에서만 읽고 미설정 시 임시 키로 대체하지 않는다.
@Component
@ConditionalOnProperty(name = "app.otp.code-hash.algorithm", havingValue = "hmac")
public class HmacOtpCodeHashAdapter implements OtpCodeHashPort {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String DOMAIN_PREFIX = "corebank:otp:code:v1:";
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec key;

    public HmacOtpCodeHashAdapter(@Value("${APP_OTP_CODE_HASH_KEY_BASE64:}") String keyBase64) {
        byte[] keyBytes = decodeKey(keyBase64);
        this.key = new SecretKeySpec(keyBytes, ALGORITHM);
        Arrays.fill(keyBytes, (byte) 0);
    }

    @Override
    public String hash(String otpCode) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((DOMAIN_PREFIX + otpCode).getBytes(StandardCharsets.UTF_8));
            // Base64 무패딩 43자는 code_hash CHAR(60)에 들어간다. hex 64자는 들어가지 않는다.
            return Base64.getEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("OTP 번호 해시 생성에 실패했습니다.", e);
        }
    }

    @Override
    public boolean matches(String otpCode, String otpCodeHash) {
        // 일치 여부에 따라 비교 시간이 달라지지 않도록 고정 시간 비교를 쓴다.
        return MessageDigest.isEqual(
                hash(otpCode).getBytes(StandardCharsets.UTF_8), otpCodeHash.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] decodeKey(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("OTP 번호 해시 키 설정이 누락되었습니다.");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            // 키 원문이나 Base64 문자열을 예외·로그에 포함하지 않는다.
            throw new IllegalStateException("OTP 번호 해시 키 형식이 올바르지 않습니다.");
        }
        if (bytes.length != KEY_BYTES) {
            Arrays.fill(bytes, (byte) 0);
            throw new IllegalStateException("OTP 번호 해시 키는 32바이트여야 합니다.");
        }
        return bytes;
    }
}
