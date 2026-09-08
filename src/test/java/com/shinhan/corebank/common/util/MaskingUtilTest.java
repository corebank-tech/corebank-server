package com.shinhan.corebank.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MaskingUtilTest {

    @Test
    @DisplayName("정상 계좌번호는 앞 3자리·뒤 3자리만 남기고 마스킹된다")
    void validAccountNumber_masked() {
        assertThat(MaskingUtil.maskAccountNumber("110123456789")).isEqualTo("110******789");
    }

    @Test
    @DisplayName("숫자가 아닌 계좌번호는 예외 메시지에 원문이 포함되지 않는다")
    void invalidAccountNumber_exceptionMessage_doesNotContainRawInput() {
        String raw = "abcdefghijkl";

        assertThatIllegalArgumentException()
                .isThrownBy(() -> MaskingUtil.maskAccountNumber(raw))
                .withMessageNotContaining(raw);
    }

    @Test
    @DisplayName("길이가 맞지 않는 계좌번호도 예외 메시지에 원문이 포함되지 않는다")
    void wrongLengthAccountNumber_exceptionMessage_doesNotContainRawInput() {
        String raw = "1234567890123";

        assertThatIllegalArgumentException()
                .isThrownBy(() -> MaskingUtil.maskAccountNumber(raw))
                .withMessageNotContaining(raw);
    }

    @Test
    @DisplayName("null 계좌번호도 예외를 던진다")
    void nullAccountNumber_throws() {
        assertThatIllegalArgumentException().isThrownBy(() -> MaskingUtil.maskAccountNumber(null));
    }

    @Test
    @DisplayName("로컬파트가 4자를 초과하는 이메일은 앞 4자만 남기고 마스킹된다")
    void email_localPartLongerThanFour_masksRemainder() {
        assertThat(MaskingUtil.maskEmail("abcdef@test.com")).isEqualTo("abcd**@test.com");
    }

    @Test
    @DisplayName("로컬파트가 정확히 4자인 이메일은 마지막 1자만 마스킹된다")
    void email_localPartExactlyFour_masksLastCharOnly() {
        assertThat(MaskingUtil.maskEmail("abcd@test.com")).isEqualTo("abc*@test.com");
    }

    @Test
    @DisplayName("로컬파트가 1자인 이메일도 최소 1자는 마스킹된다")
    void email_localPartSingleChar_masksEntirely() {
        assertThat(MaskingUtil.maskEmail("a@test.com")).isEqualTo("*@test.com");
    }

    @Test
    @DisplayName("null 이메일은 예외를 던진다")
    void nullEmail_throws() {
        assertThatIllegalArgumentException().isThrownBy(() -> MaskingUtil.maskEmail(null));
    }

    @Test
    @DisplayName("@이 없는 이메일은 예외 메시지에 원문이 포함되지 않는다")
    void emailWithoutAtSign_exceptionMessage_doesNotContainRawInput() {
        String raw = "not-an-email";

        assertThatIllegalArgumentException()
                .isThrownBy(() -> MaskingUtil.maskEmail(raw))
                .withMessageNotContaining(raw);
    }

    @Test
    @DisplayName("@으로 시작하는 이메일은 예외 메시지에 원문이 포함되지 않는다")
    void emailStartingWithAtSign_exceptionMessage_doesNotContainRawInput() {
        String raw = "@test.com";

        assertThatIllegalArgumentException()
                .isThrownBy(() -> MaskingUtil.maskEmail(raw))
                .withMessageNotContaining(raw);
    }

    @Test
    @DisplayName("@으로 끝나는 이메일은 예외 메시지에 원문이 포함되지 않는다")
    void emailEndingWithAtSign_exceptionMessage_doesNotContainRawInput() {
        String raw = "abcdef@";

        assertThatIllegalArgumentException()
                .isThrownBy(() -> MaskingUtil.maskEmail(raw))
                .withMessageNotContaining(raw);
    }

    @Test
    @DisplayName("정상 휴대폰번호는 앞 3자리·뒤 4자리만 남기고 중간 4자리가 마스킹된다")
    void validPhoneNumber_masked() {
        assertThat(MaskingUtil.maskPhoneNumber("01012345678")).isEqualTo("010****5678");
    }

    @Test
    @DisplayName("null 휴대폰번호는 예외를 던진다")
    void nullPhoneNumber_throws() {
        assertThatIllegalArgumentException().isThrownBy(() -> MaskingUtil.maskPhoneNumber(null));
    }

    @Test
    @DisplayName("하이픈이 포함된 휴대폰번호는 예외 메시지에 원문이 포함되지 않는다")
    void phoneNumberWithHyphen_exceptionMessage_doesNotContainRawInput() {
        String raw = "010-1234-5678";

        assertThatIllegalArgumentException()
                .isThrownBy(() -> MaskingUtil.maskPhoneNumber(raw))
                .withMessageNotContaining(raw);
    }

    @Test
    @DisplayName("길이가 맞지 않는 휴대폰번호는 예외 메시지에 원문이 포함되지 않는다")
    void wrongLengthPhoneNumber_exceptionMessage_doesNotContainRawInput() {
        String raw = "0101234567";

        assertThatIllegalArgumentException()
                .isThrownBy(() -> MaskingUtil.maskPhoneNumber(raw))
                .withMessageNotContaining(raw);
    }
}
