package com.shinhan.corebank.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProductRateTierIdTest {

    @ParameterizedTest(name = "productId={0}, termMonths={1}")
    @CsvSource(
            nullValues = "null",
            value = {"null, 12", "1,    null", "null, null"})
    @DisplayName("productId 또는 termMonths가 null이면 CMN0002를 던진다")
    void rejectsNull(Long productId, Short termMonths) {
        assertThatThrownBy(() -> new ProductRateTierId(productId, termMonths))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("두 값이 모두 있으면 정상 생성된다")
    void createsWithBothValues() {
        ProductRateTierId id = new ProductRateTierId(1L, (short) 12);

        assertThat(id.getProductId()).isEqualTo(1L);
        assertThat(id.getTermMonths()).isEqualTo((short) 12);
    }

    @Test
    @DisplayName("값이 같으면 동등하고 해시도 같다")
    void equalsAndHashCode() {
        var id = new ProductRateTierId(1L, (short) 12);

        assertThat(id)
                .isEqualTo(new ProductRateTierId(1L, (short) 12))
                .hasSameHashCodeAs(new ProductRateTierId(1L, (short) 12));
        assertThat(id).isNotEqualTo(new ProductRateTierId(1L, (short) 24));
    }
}
