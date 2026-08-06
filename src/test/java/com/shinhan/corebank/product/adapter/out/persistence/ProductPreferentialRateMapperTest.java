package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.product.domain.ProductPreferentialRate;
import com.shinhan.corebank.product.domain.ProductPreferentialRateId;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductPreferentialRateMapperTest {

    @Test
    @DisplayName("JPA 엔티티를 도메인 객체로 변환하면 모든 필드가 보존된다")
    void toDomain_preservesAllFields() {
        ProductPreferentialRateJpaEntity entity = ProductPreferentialRateJpaEntity.builder()
                .productPreferentialRateId(new ProductPreferentialRateJpaEntityId(1L, "LONG_TERM"))
                .conditionName("장기거래 우대")
                .rate(new BigDecimal("0.30"))
                .build();

        ProductPreferentialRate domain = ProductPreferentialRateMapper.toDomain(entity);

        assertThat(domain.getProductPreferentialRateId().getProductId())
                .isEqualTo(entity.getProductPreferentialRateId().getProductId());
        assertThat(domain.getProductPreferentialRateId().getConditionCode())
                .isEqualTo(entity.getProductPreferentialRateId().getConditionCode());
        assertThat(domain.getConditionName()).isEqualTo(entity.getConditionName());
        assertThat(domain.getRate()).isEqualByComparingTo(entity.getRate());
    }

    @Test
    @DisplayName("도메인 객체를 JPA 엔티티로 변환하면 모든 필드가 보존된다")
    void toEntity_preservesAllFields() {
        ProductPreferentialRate domain = ProductPreferentialRate.builder()
                .productPreferentialRateId(new ProductPreferentialRateId(1L, "LONG_TERM"))
                .conditionName("장기거래 우대")
                .rate(new BigDecimal("0.30"))
                .build();

        ProductPreferentialRateJpaEntity entity = ProductPreferentialRateMapper.toEntity(domain);

        assertThat(entity.getProductPreferentialRateId().getProductId())
                .isEqualTo(domain.getProductPreferentialRateId().getProductId());
        assertThat(entity.getProductPreferentialRateId().getConditionCode())
                .isEqualTo(domain.getProductPreferentialRateId().getConditionCode());
        assertThat(entity.getConditionName()).isEqualTo(domain.getConditionName());
        assertThat(entity.getRate()).isEqualByComparingTo(domain.getRate());
    }
}
