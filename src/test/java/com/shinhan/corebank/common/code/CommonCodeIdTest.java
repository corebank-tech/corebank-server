package com.shinhan.corebank.common.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CommonCodeIdTest {

    @ParameterizedTest(name = "codeGroup={0}, code={1}")
    @CsvSource(
            nullValues = "null",
            value = {"null, ACTIVE", "ACCOUNT_STATUS, null", "null, null"})
    @DisplayName("codeGroup 또는 code가 null이면 CMN0002를 던진다")
    void rejectsNull(String codeGroup, String code) {
        assertThatThrownBy(() -> new CommonCodeId(codeGroup, code))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @ParameterizedTest(name = "blank=\"{0}\"")
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("codeGroup 또는 code가 공백이면 CMN0002를 던진다")
    void rejectsBlank(String blank) {
        assertThatThrownBy(() -> new CommonCodeId(blank, "ACTIVE")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new CommonCodeId("ACCOUNT_STATUS", blank)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("값이 같으면 동등하고 해시도 같다")
    void equalsAndHashCode() {
        CommonCodeId id = new CommonCodeId("ACCOUNT_STATUS", "ACTIVE");

        assertThat(id)
                .isEqualTo(new CommonCodeId("ACCOUNT_STATUS", "ACTIVE"))
                .hasSameHashCodeAs(new CommonCodeId("ACCOUNT_STATUS", "ACTIVE"));
        assertThat(id).isNotEqualTo(new CommonCodeId("ACCOUNT_STATUS", "CLOSED"));
    }

    @Test
    @DisplayName("무인자 생성자는 Hibernate 전용이므로 protected 로 제한한다")
    void restrictsNoArgConstructorToProtected() throws NoSuchMethodException {
        Constructor<CommonCodeId> constructor = CommonCodeId.class.getDeclaredConstructor();

        assertThat(Modifier.isProtected(constructor.getModifiers())).isTrue();
    }
}
