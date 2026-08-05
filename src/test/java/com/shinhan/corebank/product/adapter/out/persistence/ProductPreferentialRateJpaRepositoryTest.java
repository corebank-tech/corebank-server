package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.domain.ProductPreferentialRate;
import com.shinhan.corebank.product.domain.ProductPreferentialRateId;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProductPreferentialRateJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    ProductJpaRepository productRepository;

    @Autowired
    ProductPreferentialRateJpaRepository repository;

    @Autowired
    EntityManager entityManager;

    Long productId;

    @BeforeEach
    void setUp() {
        productId = productRepository.save(ProductTestFixtures.defaultProduct()).getProductId();
    }

    @Test
    @DisplayName("ProductPreferentialRate를 저장하면 복합키로 조회된다")
    void saveAndFindById() {
        ProductPreferentialRateId id = new ProductPreferentialRateId(productId, "LONG_TERM");
        ProductPreferentialRate preferentialRate = ProductPreferentialRate.builder()
                .productPreferentialRateId(id)
                .conditionName("장기거래 우대")
                .rate(new BigDecimal("0.30"))
                .build();

        repository.save(preferentialRate);
        entityManager.flush();
        entityManager.clear();

        ProductPreferentialRate found = repository.findById(id).orElseThrow();
        assertThat(found.getConditionName()).isEqualTo("장기거래 우대");
        assertThat(found.getRate()).isEqualByComparingTo("0.30");
    }
}
