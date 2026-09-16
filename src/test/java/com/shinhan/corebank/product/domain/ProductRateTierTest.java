package com.shinhan.corebank.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductRateTierTest {

    @Test
    @DisplayName("id 없이 생성하면 CMN0002를 던진다")
    void rejectsNullId() {
        assertThatThrownBy(() ->
                        ProductRateTier.builder().rate(new BigDecimal("2.80")).build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("rate 없이 생성하면 CMN0002를 던진다")
    void rejectsNullRate() {
        assertThatThrownBy(() -> ProductRateTier.builder()
                        .id(new ProductRateTierId(1L, (short) 12))
                        .build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("모든 값이 있으면 정상 생성된다")
    void createsWithAllValues() {
        ProductRateTier tier = ProductRateTier.builder()
                .id(new ProductRateTierId(1L, (short) 12))
                .rate(new BigDecimal("2.80"))
                .build();

        assertThat(tier.getId().getTermMonths()).isEqualTo((short) 12);
        assertThat(tier.getRate()).isEqualByComparingTo("2.80");
    }
}
