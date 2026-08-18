package com.shinhan.corebank.transfer.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.domain.exception.FavoriteAccountErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AliasLengthValidatorTest {

    @Test
    @DisplayName("한글 12자(weight 24)는 통과한다")
    void validate_korean12Chars_passes() {
        assertThatCode(() -> AliasLengthValidator.validate("가".repeat(12)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("한글 13자(weight 26)는 FAV0001을 던진다")
    void validate_korean13Chars_throwsAliasLengthExceeded() {
        assertThatThrownBy(() -> AliasLengthValidator.validate("가".repeat(13)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getErrorCode()).isEqualTo(FavoriteAccountErrorCode.ALIAS_LENGTH_EXCEEDED);
                });
    }

    @Test
    @DisplayName("영문/숫자 24자(weight 24)는 통과한다")
    void validate_alphanumeric24Chars_passes() {
        assertThatCode(() -> AliasLengthValidator.validate("a".repeat(12) + "1".repeat(12)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("영문/숫자 25자(weight 25)는 FAV0001을 던진다")
    void validate_alphanumeric25Chars_throwsAliasLengthExceeded() {
        assertThatThrownBy(() -> AliasLengthValidator.validate("a".repeat(25)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("한글 11자 + 영문 2자(weight 22+2=24)는 통과한다")
    void validate_mixedExactBoundary_passes() {
        assertThatCode(() -> AliasLengthValidator.validate("가".repeat(11) + "ab"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("한글 11자 + 영문 3자(weight 22+3=25)는 FAV0001을 던진다")
    void validate_mixedOverBoundary_throwsAliasLengthExceeded() {
        assertThatThrownBy(() -> AliasLengthValidator.validate("가".repeat(11) + "abc"))
                .isInstanceOf(BusinessException.class);
    }
}
