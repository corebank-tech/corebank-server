package com.shinhan.corebank.customer.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomerEmailBlindIndexTest {

    private final CustomerEmailBlindIndex blindIndex = new CustomerEmailBlindIndex(testKey((byte) 1));

    @Test
    @DisplayName("동일 이메일은 같은 검색 해시가 생성된다")
    void sameEmailProducesSameHash() {
        assertThat(blindIndex.hash("user@example.com")).isEqualTo(blindIndex.hash("user@example.com"));
    }

    @Test
    @DisplayName("대소문자와 양쪽 공백을 정규화해서 같은 검색 해시를 만든다")
    void normalizesEmailBeforeHashing() {
        assertThat(blindIndex.hash(" User@Example.COM ")).isEqualTo(blindIndex.hash("user@example.com"));
    }

    @Test
    @DisplayName("다른 이메일은 다른 검색 해시가 생성된다")
    void differentEmailsProduceDifferentHashes() {
        assertThat(blindIndex.hash("first@example.com")).isNotEqualTo(blindIndex.hash("second@example.com"));
    }

    @Test
    @DisplayName("서로 다른 비밀키는 같은 이메일에서도 다른 검색 해시를 만든다")
    void differentKeysProduceDifferentHashes() {
        CustomerEmailBlindIndex another = new CustomerEmailBlindIndex(testKey((byte) 2));

        assertThat(blindIndex.hash("same@example.com")).isNotEqualTo(another.hash("same@example.com"));
    }

    @Test
    @DisplayName("해시는 DB CHAR(64)에 저장 가능한 64자리 소문자 16진수다")
    void hashIsLowercaseHexSha256() {
        assertThat(blindIndex.hash("user@example.com")).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("null·공백 이메일은 원문을 출력하지 않고 거부한다")
    void emptyEmailRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> blindIndex.hash(null));
        assertThatIllegalArgumentException().isThrownBy(() -> blindIndex.hash("  "));
    }

    @Test
    @DisplayName("검색 해시 키 길이가 32바이트가 아니면 초기화할 수 없다")
    void invalidKeyRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CustomerEmailBlindIndex(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new CustomerEmailBlindIndex(new byte[16]));
        assertThatIllegalArgumentException().isThrownBy(() -> new CustomerEmailBlindIndex(new byte[33]));
    }

    @Test
    @DisplayName("생성자에 전달한 키 배열을 변경해도 해시값은 변하지 않는다")
    void defensiveKeyCopy() {
        byte[] key = testKey((byte) 7);
        CustomerEmailBlindIndex ownIndex = new CustomerEmailBlindIndex(key);
        String firstHash = ownIndex.hash("user@example.com");
        Arrays.fill(key, (byte) 0);

        assertThat(ownIndex.hash("user@example.com")).isEqualTo(firstHash);
    }

    private static byte[] testKey(byte value) {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return key;
    }
}
