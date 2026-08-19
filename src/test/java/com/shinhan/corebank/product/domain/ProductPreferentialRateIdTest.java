package com.shinhan.corebank.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductPreferentialRateIdTest {

    @ParameterizedTest(name = "productId={0}, conditionCode={1}")
    @CsvSource(nullValues = "null", value = {
            "null, LONG_TERM",
            "1,    null",
            "null, null"
    })
    @DisplayName("productId 또는 conditionCode가 null이면 CMN0002를 던진다")
    void rejectsNull(Long productId, String conditionCode) {
        assertThatThrownBy(() -> new ProductPreferentialRateId(productId, conditionCode))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @ParameterizedTest(name = "conditionCode=\"{0}\"")
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("conditionCode가 공백이면 CMN0002를 던진다")
    void rejectsBlankConditionCode(String blank) {
        assertThatThrownBy(() -> new ProductPreferentialRateId(1L, blank))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("두 값이 모두 있으면 정상 생성된다")
    void createsWithBothValues() {
        ProductPreferentialRateId id = new ProductPreferentialRateId(1L, "LONG_TERM");

        assertThat(id.getProductId()).isEqualTo(1L);
        assertThat(id.getConditionCode()).isEqualTo("LONG_TERM");
    }

    @Test
    @DisplayName("값이 같으면 동등하고 해시도 같다")
    void equalsAndHashCode() {
        ProductPreferentialRateId id = new ProductPreferentialRateId(1L, "LONG_TERM");

        assertThat(id)
                .isEqualTo(new ProductPreferentialRateId(1L, "LONG_TERM"))
                .hasSameHashCodeAs(new ProductPreferentialRateId(1L, "LONG_TERM"));
        assertThat(id).isNotEqualTo(new ProductPreferentialRateId(1L, "NEW_CUSTOMER"));
    }
}
