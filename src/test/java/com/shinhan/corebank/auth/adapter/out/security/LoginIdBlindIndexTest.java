package com.shinhan.corebank.auth.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 같은 아이디가 항상 같은 행을 찾는지, 키가 실제로 쓰이는지, 키 설정 오류가 기동을 막는지 검증한다.
class LoginIdBlindIndexTest {

    private static final String KEY = encodedTestKey(32, (byte) 1);
    private static final String OTHER_KEY = encodedTestKey(32, (byte) 2);

    @Test
    @DisplayName("같은 아이디는 같은 해시, 다른 아이디는 다른 해시를 만든다")
    void hashesDeterministically() {
        // Given
        final LoginIdBlindIndex blindIndex = new LoginIdBlindIndex(KEY);

        // When
        final String hash = blindIndex.hash("ph08ghost01");

        // Then
        assertThat(hash).isEqualTo(blindIndex.hash("ph08ghost01"));
        assertThat(hash).isNotEqualTo(blindIndex.hash("ph08ghost02"));
    }

    @Test
    @DisplayName("해시는 소문자 hex 64자로 user_id_hash CHAR(64)에 들어간다")
    void producesHexThatFitsColumn() {
        // Given
        final LoginIdBlindIndex blindIndex = new LoginIdBlindIndex(KEY);

        // When
        final String hash = blindIndex.hash("ph08ghost01");

        // Then
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("키가 다르면 같은 아이디도 다른 해시가 되어 키 없이는 아이디를 역산할 수 없다")
    void keyChangesHash() {
        // Given
        final LoginIdBlindIndex blindIndex = new LoginIdBlindIndex(KEY);
        final LoginIdBlindIndex otherKeyBlindIndex = new LoginIdBlindIndex(OTHER_KEY);

        // When & Then
        assertThat(blindIndex.hash("ph08ghost01")).isNotEqualTo(otherKeyBlindIndex.hash("ph08ghost01"));
    }

    @Test
    @DisplayName("키가 비어 있으면 환경변수 이름을 알려 주며 기동을 실패시킨다")
    void rejectsMissingKey() {
        // When
        final IllegalStateException exception =
                catchThrowableOfType(IllegalStateException.class, () -> new LoginIdBlindIndex(""));

        // Then
        assertThat(exception).hasMessage("APP_LOGIN_ID_HMAC_KEY_BASE64 환경변수가 없습니다.");
    }

    @Test
    @DisplayName("키가 32바이트가 아니면 기동을 실패시키고 키 원문을 메시지에 담지 않는다")
    void rejectsKeyOfWrongLength() {
        // Given
        final String shortKey = encodedTestKey(16, (byte) 1);

        // When
        final IllegalStateException exception =
                catchThrowableOfType(IllegalStateException.class, () -> new LoginIdBlindIndex(shortKey));

        // Then
        assertThat(exception).hasMessage("APP_LOGIN_ID_HMAC_KEY_BASE64 값은 Base64로 디코딩해 32바이트여야 합니다.");
        assertThat(exception.getMessage()).doesNotContain(shortKey);
    }

    @Test
    @DisplayName("Base64가 아닌 키는 형식 오류로 막고 키 문자열을 메시지에 담지 않는다")
    void rejectsNonBase64Key() {
        // When
        final IllegalStateException exception =
                catchThrowableOfType(IllegalStateException.class, () -> new LoginIdBlindIndex("not*base64!"));

        // Then
        assertThat(exception).hasMessage("APP_LOGIN_ID_HMAC_KEY_BASE64 값이 Base64 형식이 아닙니다.");
        assertThat(exception.getMessage()).doesNotContain("not*base64!");
    }

    // 키 문자열을 소스에 박지 않고 길이만 맞춰 만든다.
    private static String encodedTestKey(int length, byte fill) {
        final byte[] key = new byte[length];
        Arrays.fill(key, fill);
        return Base64.getEncoder().encodeToString(key);
    }
}
