package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.product.domain.ProductRateTier;
import com.shinhan.corebank.product.domain.ProductRateTierId;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductRateTierMapperTest {

    @Test
    @DisplayName("JPA 엔티티를 도메인 객체로 변환하면 모든 필드가 보존된다")
    void toDomain_preservesAllFields() {
        ProductRateTierJpaEntity entity = ProductRateTierJpaEntity.builder()
                .id(new ProductRateTierJpaEntityId(1L, (short) 12))
                .rate(new BigDecimal("2.80"))
                .build();

        ProductRateTier domain = ProductRateTierMapper.toDomain(entity);

        assertThat(domain.getId().getProductId()).isEqualTo(entity.getId().getProductId());
        assertThat(domain.getId().getTermMonths()).isEqualTo(entity.getId().getTermMonths());
        assertThat(domain.getRate()).isEqualByComparingTo(entity.getRate());
    }

    @Test
    @DisplayName("도메인 객체를 JPA 엔티티로 변환하면 모든 필드가 보존된다")
    void toEntity_preservesAllFields() {
        ProductRateTier domain = ProductRateTier.builder()
                .id(new ProductRateTierId(1L, (short) 12))
                .rate(new BigDecimal("2.80"))
                .build();

        ProductRateTierJpaEntity entity = ProductRateTierMapper.toEntity(domain);

        assertThat(entity.getId().getProductId()).isEqualTo(domain.getId().getProductId());
        assertThat(entity.getId().getTermMonths()).isEqualTo(domain.getId().getTermMonths());
        assertThat(entity.getRate()).isEqualByComparingTo(domain.getRate());
    }
}
