package com.shinhan.corebank.otp.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 키가 실제로 해시에 쓰이는지, 저장 길이가 code_hash CHAR(60)에 들어가는지 검증한다.
class HmacOtpCodeHashAdapterTest {

    private static final String KEY = encodedTestKey(32, (byte) 1);
    private static final String OTHER_KEY = encodedTestKey(32, (byte) 2);

    @Test
    @DisplayName("같은 번호는 같은 해시, 다른 번호는 다른 해시를 만든다")
    void hashesDeterministically() {
        // Given
        HmacOtpCodeHashAdapter adapter = new HmacOtpCodeHashAdapter(KEY);

        // When
        String hash = adapter.hash("012345");

        // Then
        assertThat(hash).isEqualTo(adapter.hash("012345"));
        assertThat(hash).isNotEqualTo(adapter.hash("012346"));
    }

    @Test
    @DisplayName("해시는 Base64 43자로 code_hash CHAR(60)에 들어간다")
    void producesHashShortEnoughForColumn() {
        // Given
        HmacOtpCodeHashAdapter adapter = new HmacOtpCodeHashAdapter(KEY);

        // When
        String hash = adapter.hash("012345");

        // Then
        assertThat(hash).hasSize(43);
        assertThat(hash).matches("[A-Za-z0-9+/]+");
    }

    @Test
    @DisplayName("키가 다르면 같은 번호도 다른 해시가 되어 키 없이는 후보를 계산할 수 없다")
    void keyChangesHash() {
        // Given
        HmacOtpCodeHashAdapter adapter = new HmacOtpCodeHashAdapter(KEY);
        HmacOtpCodeHashAdapter otherKeyAdapter = new HmacOtpCodeHashAdapter(OTHER_KEY);

        // When & Then
        assertThat(adapter.hash("012345")).isNotEqualTo(otherKeyAdapter.hash("012345"));
    }

    @Test
    @DisplayName("자기 해시는 일치, 다른 번호의 해시는 불일치로 판정한다")
    void matchesOwnHashOnly() {
        // Given
        HmacOtpCodeHashAdapter adapter = new HmacOtpCodeHashAdapter(KEY);
        String hash = adapter.hash("012345");

        // When & Then
        assertThat(adapter.matches("012345", hash)).isTrue();
        assertThat(adapter.matches("999999", hash)).isFalse();
    }

    @Test
    @DisplayName("키가 비어 있으면 임시 키로 대체하지 않고 기동을 실패시킨다")
    void rejectsMissingKey() {
        // When
        IllegalStateException exception =
                catchThrowableOfType(IllegalStateException.class, () -> new HmacOtpCodeHashAdapter(""));

        // Then
        assertThat(exception).hasMessage("OTP 번호 해시 키 설정이 누락되었습니다.");
    }

    @Test
    @DisplayName("키가 32바이트가 아니면 기동을 실패시키고 키 원문을 메시지에 담지 않는다")
    void rejectsKeyOfWrongLength() {
        // Given
        String shortKey = encodedTestKey(16, (byte) 1);

        // When
        IllegalStateException exception =
                catchThrowableOfType(IllegalStateException.class, () -> new HmacOtpCodeHashAdapter(shortKey));

        // Then
        assertThat(exception).hasMessage("OTP 번호 해시 키는 32바이트여야 합니다.");
        assertThat(exception.getMessage()).doesNotContain(shortKey);
    }

    @Test
    @DisplayName("Base64가 아닌 키는 형식 오류로 막고 키 문자열을 메시지에 담지 않는다")
    void rejectsNonBase64Key() {
        // When
        IllegalStateException exception =
                catchThrowableOfType(IllegalStateException.class, () -> new HmacOtpCodeHashAdapter("not*base64!"));

        // Then
        assertThat(exception).hasMessage("OTP 번호 해시 키 형식이 올바르지 않습니다.");
        assertThat(exception.getMessage()).doesNotContain("not*base64!");
    }

    // 키 문자열을 소스에 박지 않고 길이만 맞춰 만든다.
    private static String encodedTestKey(int length, byte fill) {
        byte[] key = new byte[length];
        Arrays.fill(key, fill);
        return Base64.getEncoder().encodeToString(key);
    }
}
