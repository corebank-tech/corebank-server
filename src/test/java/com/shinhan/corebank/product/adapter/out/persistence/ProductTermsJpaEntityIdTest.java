package com.shinhan.corebank.product.adapter.out.persistence;

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

class ProductTermsJpaEntityIdTest {

    @ParameterizedTest(name = "productId={0}, termsId={1}")
    @CsvSource(nullValues = "null", value = {
            "null, 2",
            "1,    null",
            "null, null"
    })
    @DisplayName("productId 또는 termsId가 null이면 CMN0002를 던진다")
    void rejectsNull(Long productId, Long termsId) {
        assertThatThrownBy(() -> new ProductTermsJpaEntityId(productId, termsId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("두 값이 모두 있으면 정상 생성된다")
    void createsWithBothValues() {
        ProductTermsJpaEntityId id = new ProductTermsJpaEntityId(1L, 2L);

        assertThat(id.getProductId()).isEqualTo(1L);
        assertThat(id.getTermsId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Hibernate가 쓰는 기본 생성자는 유지된다")
    void keepsNoArgConstructorForHibernate() {
        ProductTermsJpaEntityId id = new ProductTermsJpaEntityId();

        assertThat(id.getProductId()).isNull();
    }

    @Test
    @DisplayName("무인자 생성자는 Hibernate 전용이므로 protected 로 제한한다")
    void restrictsNoArgConstructorToProtected() throws NoSuchMethodException {
        Constructor<ProductTermsJpaEntityId> constructor = ProductTermsJpaEntityId.class.getDeclaredConstructor();

        assertThat(Modifier.isProtected(constructor.getModifiers())).isTrue();
    }
}
