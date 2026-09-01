package com.shinhan.corebank.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductPreferentialRateTest {

    @Test
    @DisplayName("productPreferentialRateId 없이 생성하면 CMN0002를 던진다")
    void rejectsNullId() {
        assertThatThrownBy(() -> ProductPreferentialRate.builder()
                        .conditionName("장기거래 우대")
                        .rate(new BigDecimal("0.30"))
                        .build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("conditionName이 공백이면 CMN0002를 던진다")
    void rejectsBlankConditionName() {
        assertThatThrownBy(() -> ProductPreferentialRate.builder()
                        .productPreferentialRateId(new ProductPreferentialRateId(1L, "LONG_TERM"))
                        .conditionName("   ")
                        .rate(new BigDecimal("0.30"))
                        .build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("rate 없이 생성하면 CMN0002를 던진다")
    void rejectsNullRate() {
        assertThatThrownBy(() -> ProductPreferentialRate.builder()
                        .productPreferentialRateId(new ProductPreferentialRateId(1L, "LONG_TERM"))
                        .conditionName("장기거래 우대")
                        .build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("모든 값이 있으면 정상 생성된다")
    void createsWithAllValues() {
        ProductPreferentialRate rate = ProductPreferentialRate.builder()
                .productPreferentialRateId(new ProductPreferentialRateId(1L, "LONG_TERM"))
                .conditionName("장기거래 우대")
                .rate(new BigDecimal("0.30"))
                .build();

        assertThat(rate.getProductPreferentialRateId().getConditionCode()).isEqualTo("LONG_TERM");
        assertThat(rate.getConditionName()).isEqualTo("장기거래 우대");
    }
}
