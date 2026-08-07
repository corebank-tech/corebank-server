package com.shinhan.corebank.product.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductTermsTest {

    @Test
    @DisplayName("displayOrder 없이 생성하면 NullPointerException을 던진다")
    void rejectsNullDisplayOrder() {
        assertThatThrownBy(() -> ProductTerms.builder()
                .id(new ProductTermsId(1L, 2L))
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("id 없이 생성하면 NullPointerException을 던진다")
    void rejectsNullId() {
        assertThatThrownBy(() -> ProductTerms.builder()
                .displayOrder((short) 1)
                .build())
                .isInstanceOf(NullPointerException.class);
    }
}
