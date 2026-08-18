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
}
