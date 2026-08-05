package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.domain.ProductRateTier;
import com.shinhan.corebank.product.domain.ProductRateTierId;
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
        ProductRateTierId id = new ProductRateTierId(productId, (short) 12);
        ProductRateTier tier = ProductRateTier.builder()
                .id(id)
                .rate(new BigDecimal("2.80"))
                .build();

        repository.save(tier);
        entityManager.flush();
        entityManager.clear();

        ProductRateTier found = repository.findById(id).orElseThrow();
        assertThat(found.getRate()).isEqualByComparingTo("2.80");
    }
}
