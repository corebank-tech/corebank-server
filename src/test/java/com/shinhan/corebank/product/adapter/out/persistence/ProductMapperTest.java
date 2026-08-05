package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.product.domain.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductMapperTest {

    @Test
    @DisplayName("JPA 엔티티를 도메인 객체로 변환하면 모든 필드가 보존된다")
    void toDomain_preservesAllFields() {
        ProductJpaEntity entity = ProductTestFixtures.defaultProduct();

        Product domain = ProductMapper.toDomain(entity);

        assertThat(domain.getProductId()).isEqualTo(entity.getProductId());
        assertThat(domain.getProductCode()).isEqualTo(entity.getProductCode());
        assertThat(domain.getProductName()).isEqualTo(entity.getProductName());
        assertThat(domain.getProductGroup()).isEqualTo(entity.getProductGroup());
        assertThat(domain.getDepositType()).isEqualTo(entity.getDepositType());
        assertThat(domain.getSummary()).isEqualTo(entity.getSummary());
        assertThat(domain.getDescription()).isEqualTo(entity.getDescription());
        assertThat(domain.getBaseRate()).isEqualByComparingTo(entity.getBaseRate());
        assertThat(domain.getMaxRate()).isEqualByComparingTo(entity.getMaxRate());
        assertThat(domain.getMinAmount()).isEqualTo(entity.getMinAmount());
        assertThat(domain.getMaxAmount()).isEqualTo(entity.getMaxAmount());
        assertThat(domain.getAmountUnit()).isEqualTo(entity.getAmountUnit());
        assertThat(domain.getMinTermMonths()).isEqualTo(entity.getMinTermMonths());
        assertThat(domain.getMaxTermMonths()).isEqualTo(entity.getMaxTermMonths());
        assertThat(domain.getInterestPayType()).isEqualTo(entity.getInterestPayType());
        assertThat(domain.getSaleStatus()).isEqualTo(entity.getSaleStatus());
        assertThat(domain.getSaleStartDate()).isEqualTo(entity.getSaleStartDate());
        assertThat(domain.getSaleEndDate()).isEqualTo(entity.getSaleEndDate());
        assertThat(domain.getNewFlag()).isEqualTo(entity.getNewFlag());
        assertThat(domain.getSingleAccountLimit()).isEqualTo(entity.getSingleAccountLimit());
        assertThat(domain.getCreatedAt()).isEqualTo(entity.getCreatedAt());
        assertThat(domain.getUpdatedAt()).isEqualTo(entity.getUpdatedAt());
    }

    @Test
    @DisplayName("도메인 객체를 JPA 엔티티로 변환하면 모든 필드가 보존된다")
    void toEntity_preservesAllFields() {
        Product domain = ProductTestFixtures.defaultProductDomain();

        ProductJpaEntity entity = ProductMapper.toEntity(domain);

        assertThat(entity.getProductId()).isEqualTo(domain.getProductId());
        assertThat(entity.getProductCode()).isEqualTo(domain.getProductCode());
        assertThat(entity.getProductName()).isEqualTo(domain.getProductName());
        assertThat(entity.getProductGroup()).isEqualTo(domain.getProductGroup());
        assertThat(entity.getDepositType()).isEqualTo(domain.getDepositType());
        assertThat(entity.getSummary()).isEqualTo(domain.getSummary());
        assertThat(entity.getDescription()).isEqualTo(domain.getDescription());
        assertThat(entity.getBaseRate()).isEqualByComparingTo(domain.getBaseRate());
        assertThat(entity.getMaxRate()).isEqualByComparingTo(domain.getMaxRate());
        assertThat(entity.getMinAmount()).isEqualTo(domain.getMinAmount());
        assertThat(entity.getMaxAmount()).isEqualTo(domain.getMaxAmount());
        assertThat(entity.getAmountUnit()).isEqualTo(domain.getAmountUnit());
        assertThat(entity.getMinTermMonths()).isEqualTo(domain.getMinTermMonths());
        assertThat(entity.getMaxTermMonths()).isEqualTo(domain.getMaxTermMonths());
        assertThat(entity.getInterestPayType()).isEqualTo(domain.getInterestPayType());
        assertThat(entity.getSaleStatus()).isEqualTo(domain.getSaleStatus());
        assertThat(entity.getSaleStartDate()).isEqualTo(domain.getSaleStartDate());
        assertThat(entity.getSaleEndDate()).isEqualTo(domain.getSaleEndDate());
        assertThat(entity.getNewFlag()).isEqualTo(domain.getNewFlag());
        assertThat(entity.getSingleAccountLimit()).isEqualTo(domain.getSingleAccountLimit());
    }
}
