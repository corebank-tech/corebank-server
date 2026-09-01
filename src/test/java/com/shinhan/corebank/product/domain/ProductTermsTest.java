package com.shinhan.corebank.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductTermsTest {

    @Test
    @DisplayName("displayOrder 없이 생성하면 CMN0002를 던진다")
    void rejectsNullDisplayOrder() {
        assertThatThrownBy(() ->
                        ProductTerms.builder().id(new ProductTermsId(1L, 2L)).build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("id 없이 생성하면 CMN0002를 던진다")
    void rejectsNullId() {
        assertThatThrownBy(() -> ProductTerms.builder().displayOrder((short) 1).build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }
}
