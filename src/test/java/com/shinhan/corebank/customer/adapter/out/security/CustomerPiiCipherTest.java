package com.shinhan.corebank.customer.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomerPiiCipherTest {

    private final CustomerPiiCipher cipher = new CustomerPiiCipher(testKey((byte) 1));

    @Test
    @DisplayName("한글과 특수문자가 포함된 이메일을 암호화한 뒤 원문으로 복호화한다")
    void emailRoundTrip() {
        String email = "테스트.user+tag@example.com";

        String encrypted = cipher.encrypt(email);

        assertThat(encrypted).startsWith("v1:").doesNotContain(email);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(email);
    }

    @Test
    @DisplayName("휴대폰 번호를 암호화한 뒤 원문으로 복호화한다")
    void phoneRoundTrip() {
        String phoneNumber = "01012345678";

        String encrypted = cipher.encrypt(phoneNumber);

        assertThat(encrypted).doesNotContain(phoneNumber);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(phoneNumber);
    }

    @Test
    @DisplayName("같은 값도 매번 다른 nonce로 암호화된다")
    void freshNoncePerEncryption() {
        String first = cipher.encrypt("same@example.com");
        String second = cipher.encrypt("same@example.com");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("same@example.com");
        assertThat(cipher.decrypt(second)).isEqualTo("same@example.com");
    }

    @Test
    @DisplayName("암호문 또는 인증 태그가 변조되면 복호화가 실패한다")
    void tamperedCiphertextRejected() {
        String plaintext = "secret@example.com";
        String encrypted = cipher.encrypt(plaintext);
        byte[] payload = Base64.getUrlDecoder().decode(encrypted.substring("v1:".length()));
        payload[payload.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);

        assertThatIllegalStateException()
                .isThrownBy(() -> cipher.decrypt(tampered))
                .withMessageNotContaining(plaintext);
    }

    @Test
    @DisplayName("다른 키로는 복호화할 수 없다")
    void wrongKeyRejected() {
        String encrypted = cipher.encrypt("secret@example.com");
        CustomerPiiCipher otherCipher = new CustomerPiiCipher(testKey((byte) 2));

        assertThatIllegalStateException().isThrownBy(() -> otherCipher.decrypt(encrypted));
    }

    @Test
    @DisplayName("지원하지 않는 버전 또는 평문은 암호문으로 처리하지 않는다")
    void unknownVersionRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> cipher.decrypt("v2:abcd"));
        assertThatIllegalArgumentException().isThrownBy(() -> cipher.decrypt("plain@example.com"));
    }

    @Test
    @DisplayName("Base64 형식이 잘못됐거나 인증 태그보다 짧으면 거부한다")
    void malformedPayloadRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> cipher.decrypt("v1:!"));
        assertThatIllegalArgumentException().isThrownBy(() -> cipher.decrypt("v1:AA"));
    }

    @Test
    @DisplayName("잘못된 키 길이는 초기화 단계에서 거부한다")
    void invalidKeyLengthRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CustomerPiiCipher(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new CustomerPiiCipher(new byte[16]));
        assertThatIllegalArgumentException().isThrownBy(() -> new CustomerPiiCipher(new byte[33]));
    }

    @Test
    @DisplayName("생성자에 넘긴 원본 키 배열이 바뀌어도 암호화 키는 유지된다")
    void keyIsDefensivelyCopied() {
        byte[] key = testKey((byte) 7);
        CustomerPiiCipher ownCipher = new CustomerPiiCipher(key);
        String encrypted = ownCipher.encrypt("contact@example.com");
        Arrays.fill(key, (byte) 0);

        assertThat(ownCipher.decrypt(encrypted)).isEqualTo("contact@example.com");
    }

    @Test
    @DisplayName("null 값은 변환하지 않고 그대로 반환한다")
    void nullPassedThrough() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    private static byte[] testKey(byte value) {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return key;
    }
}
