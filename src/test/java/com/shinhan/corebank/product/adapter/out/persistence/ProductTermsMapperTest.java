package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.product.domain.ProductTerms;
import com.shinhan.corebank.product.domain.ProductTermsId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductTermsMapperTest {

    @Test
    @DisplayName("JPA 엔티티를 도메인 객체로 변환하면 복합키 필드가 보존된다")
    void toDomain_preservesAllFields() {
        ProductTermsJpaEntity entity = ProductTermsJpaEntity.builder()
                .id(new ProductTermsJpaEntityId(1L, 2L))
                .displayOrder((short) 1)
                .build();

        ProductTerms domain = ProductTermsMapper.toDomain(entity);

        assertThat(domain.getId().getProductId()).isEqualTo(entity.getId().getProductId());
        assertThat(domain.getId().getTermsId()).isEqualTo(entity.getId().getTermsId());
        assertThat(domain.getDisplayOrder()).isEqualTo(entity.getDisplayOrder());
    }

    @Test
    @DisplayName("도메인 객체를 JPA 엔티티로 변환하면 복합키 필드가 보존된다")
    void toEntity_preservesAllFields() {
        ProductTerms domain = ProductTerms.builder()
                .id(new ProductTermsId(1L, 2L))
                .displayOrder((short) 1)
                .build();

        ProductTermsJpaEntity entity = ProductTermsMapper.toEntity(domain);

        assertThat(entity.getId().getProductId()).isEqualTo(domain.getId().getProductId());
        assertThat(entity.getId().getTermsId()).isEqualTo(domain.getId().getTermsId());
        assertThat(entity.getDisplayOrder()).isEqualTo(domain.getDisplayOrder());
    }
}
