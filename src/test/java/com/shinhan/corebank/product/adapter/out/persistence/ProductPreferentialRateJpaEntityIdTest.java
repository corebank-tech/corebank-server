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
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductPreferentialRateJpaEntityIdTest {

    @ParameterizedTest(name = "conditionCode=\"{0}\"")
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("conditionCode가 null이거나 공백이면 CMN0002를 던진다")
    void rejectsNullOrBlankConditionCode(String conditionCode) {
        assertThatThrownBy(() -> new ProductPreferentialRateJpaEntityId(1L, conditionCode))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("두 값이 모두 있으면 정상 생성된다")
    void createsWithBothValues() {
        ProductPreferentialRateJpaEntityId id = new ProductPreferentialRateJpaEntityId(1L, "LONG_TERM");

        assertThat(id.getProductId()).isEqualTo(1L);
        assertThat(id.getConditionCode()).isEqualTo("LONG_TERM");
    }

    @Test
    @DisplayName("Hibernate가 쓰는 기본 생성자는 유지된다")
    void keepsNoArgConstructorForHibernate() {
        ProductPreferentialRateJpaEntityId id = new ProductPreferentialRateJpaEntityId();

        assertThat(id.getConditionCode()).isNull();
    }

    @Test
    @DisplayName("무인자 생성자는 Hibernate 전용이므로 protected 로 제한한다")
    void restrictsNoArgConstructorToProtected() throws NoSuchMethodException {
        Constructor<ProductPreferentialRateJpaEntityId> constructor =
                ProductPreferentialRateJpaEntityId.class.getDeclaredConstructor();

        assertThat(Modifier.isProtected(constructor.getModifiers())).isTrue();
    }
}
