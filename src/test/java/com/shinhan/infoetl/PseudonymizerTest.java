package com.shinhan.infoetl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PseudonymizerTest {

    private static final byte[] KEY = "test-key-for-pseudonymizer-0001".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("RFC 4231 공개 테스트 벡터와 결과가 같다")
    void matchesRfc4231Vector() {
        Pseudonymizer pseudonymizer = new Pseudonymizer("Jefe".getBytes(StandardCharsets.UTF_8));

        String actual = pseudonymizer.hmacHex("what do ya want for nothing?");

        assertThat(actual).isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    @DisplayName("같은 키와 ID는 항상 같은 값이다")
    void isDeterministic() {
        Pseudonymizer pseudonymizer = new Pseudonymizer(KEY);

        assertThat(pseudonymizer.pseudonymize(12345L)).isEqualTo(pseudonymizer.pseudonymize(12345L));
    }

    @Test
    @DisplayName("결과는 소문자 hex 64자리다")
    void returnsLowercaseHex64() {
        String actual = new Pseudonymizer(KEY).pseudonymize(1L);

        assertThat(actual).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("ID가 다르면 값이 다르다")
    void differsByIdentifier() {
        Pseudonymizer pseudonymizer = new Pseudonymizer(KEY);

        assertThat(pseudonymizer.pseudonymize(1L)).isNotEqualTo(pseudonymizer.pseudonymize(2L));
    }

    @Test
    @DisplayName("키가 다르면 같은 ID도 값이 다르다")
    void differsByKey() {
        String withKeyA = new Pseudonymizer(KEY).pseudonymize(1L);
        String withKeyB =
                new Pseudonymizer("another-key-for-pseudonymizer".getBytes(StandardCharsets.UTF_8)).pseudonymize(1L);

        assertThat(withKeyA).isNotEqualTo(withKeyB);
    }

    @Test
    @DisplayName("키가 비어 있으면 생성에 실패한다")
    void rejectsEmptyKey() {
        assertThatThrownBy(() -> new Pseudonymizer(new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }
}
