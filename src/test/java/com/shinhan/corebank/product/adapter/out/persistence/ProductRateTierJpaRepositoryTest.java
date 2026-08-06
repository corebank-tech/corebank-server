package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProductRateTierJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    ProductJpaRepository productRepository;

    @Autowired
    ProductRateTierJpaRepository repository;

    @Autowired
    EntityManager entityManager;

    Long productId;

    @BeforeEach
    void setUp() {
        productId = productRepository.save(ProductTestFixtures.defaultProduct()).getProductId();
    }

    @Test
    @DisplayName("ProductRateTier를 저장하면 복합키로 조회된다")
    void saveAndFindById() {
        ProductRateTierJpaEntityId id = new ProductRateTierJpaEntityId(productId, (short) 12);
        ProductRateTierJpaEntity tier = ProductRateTierJpaEntity.builder()
                .id(id)
                .rate(new BigDecimal("2.80"))
                .build();

        repository.save(tier);
        entityManager.flush();
        entityManager.clear();

        ProductRateTierJpaEntity found = repository.findById(id).orElseThrow();
        assertThat(found.getRate()).isEqualByComparingTo("2.80");
    }
}
