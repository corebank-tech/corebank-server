package com.shinhan.corebank.otp.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// OTP 거래정보의 빈 객체·민감 필드·날짜 형식 금지 규칙을 검증한다.
class OtpTransactionDataValidatorTest {

    private final OtpTransactionDataValidator validator = new OtpTransactionDataValidator();

    @Test
    @DisplayName("비어 있는 transactionData를 거부한다")
    void rejectsEmptyData() {
        assertThatThrownBy(() -> validator.validate(Map.of()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("중첩된 AuthToken 필드도 거부한다")
    void rejectsNestedAuthToken() {
        assertThatThrownBy(() -> validator.validate(Map.of(
                "nested", Map.of("otpAuthToken", "secret")
        ))).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("날짜 필드는 YYYY-MM-DD 형식만 허용한다")
    void rejectsInvalidDate() {
        assertThatThrownBy(() -> validator.validate(Map.of(
                "scheduledDate", "2026/08/25"
        ))).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("선택 필드를 null로 보내지 않고 제외하도록 강제한다")
    void rejectsNullValue() {
        Map<String, Object> data = new HashMap<>();
        data.put("payeeName", null);

        assertThatThrownBy(() -> validator.validate(data))
                .isInstanceOf(BusinessException.class);
    }
}
