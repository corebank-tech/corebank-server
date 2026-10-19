package com.shinhan.infoetl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PseudonymizerTest {

    private static final byte[] KEY = "test-key-for-pseudonymizer-0001-abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("RFC 4231 공개 테스트 벡터(케이스 6)와 결과가 같다")
    void matchesRfc4231Vector() {
        byte[] longKey = new byte[131];
        Arrays.fill(longKey, (byte) 0xaa);
        Pseudonymizer pseudonymizer = new Pseudonymizer(longKey);

        String actual = pseudonymizer.hmacHex("Test Using Larger Than Block-Size Key - Hash Key First");

        assertThat(actual).isEqualTo("60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54");
    }

    @Test
    @DisplayName("같은 키와 ID는 항상 같은 값이다")
    void isDeterministic() {
        Pseudonymizer pseudonymizer = new Pseudonymizer(KEY);

        assertThat(pseudonymizer.accountKey(12345L)).isEqualTo(pseudonymizer.accountKey(12345L));
        assertThat(pseudonymizer.customerKey(12345L)).isEqualTo(pseudonymizer.customerKey(12345L));
    }

    @Test
    @DisplayName("결과는 소문자 hex 64자리다")
    void returnsLowercaseHex64() {
        Pseudonymizer pseudonymizer = new Pseudonymizer(KEY);

        assertThat(pseudonymizer.accountKey(1L)).matches("^[0-9a-f]{64}$");
        assertThat(pseudonymizer.customerKey(1L)).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("ID가 다르면 값이 다르다")
    void differsByIdentifier() {
        Pseudonymizer pseudonymizer = new Pseudonymizer(KEY);

        assertThat(pseudonymizer.accountKey(1L)).isNotEqualTo(pseudonymizer.accountKey(2L));
    }

    @Test
    @DisplayName("계좌 번호와 고객 번호가 같아도 키는 다르다")
    void accountAndCustomerKeysDifferForSameNumber() {
        Pseudonymizer pseudonymizer = new Pseudonymizer(KEY);

        assertThat(pseudonymizer.accountKey(5L)).isNotEqualTo(pseudonymizer.customerKey(5L));
    }

    @Test
    @DisplayName("계좌 키와 고객 키는 종류 접두어를 붙인 입력으로 계산한다")
    void usesKindPrefixes() {
        Pseudonymizer pseudonymizer = new Pseudonymizer(KEY);

        assertThat(pseudonymizer.accountKey(5L)).isEqualTo(pseudonymizer.hmacHex("account:5"));
        assertThat(pseudonymizer.customerKey(5L)).isEqualTo(pseudonymizer.hmacHex("customer:5"));
    }

    @Test
    @DisplayName("키가 다르면 같은 ID도 값이 다르다")
    void differsByKey() {
        String withKeyA = new Pseudonymizer(KEY).accountKey(1L);
        String withKeyB =
                new Pseudonymizer("another-key-for-pseudonymizer-xyz".getBytes(StandardCharsets.UTF_8)).accountKey(1L);

        assertThat(withKeyA).isNotEqualTo(withKeyB);
    }

    @Test
    @DisplayName("키가 비어 있거나 32바이트보다 짧으면 생성에 실패한다")
    void rejectsEmptyOrShortKey() {
        assertThatThrownBy(() -> new Pseudonymizer(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pseudonymizer(new byte[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pseudonymizer(new byte[31])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("키가 정확히 32바이트면 생성된다")
    void acceptsExactly32Bytes() {
        assertThat(new Pseudonymizer(new byte[32]).accountKey(1L)).hasSize(64);
    }
}
