package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
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
        ProductPreferentialRateJpaEntityId id = new ProductPreferentialRateJpaEntityId(productId, "LONG_TERM");
        ProductPreferentialRateJpaEntity preferentialRate = ProductPreferentialRateJpaEntity.builder()
                .productPreferentialRateId(id)
                .conditionName("장기거래 우대")
                .rate(new BigDecimal("0.30"))
                .build();

        repository.save(preferentialRate);
        entityManager.flush();
        entityManager.clear();

        ProductPreferentialRateJpaEntity found = repository.findById(id).orElseThrow();
        assertThat(found.getConditionName()).isEqualTo("장기거래 우대");
        assertThat(found.getRate()).isEqualByComparingTo("0.30");
    }

    @Test
    @DisplayName("productId로 조회하면 다른 상품 것은 제외하고 conditionName 오름차순으로 반환한다")
    void findAllByProductId_returnsOwnRatesSortedByConditionName() {
        Long otherProductId = productRepository
                .save(ProductTestFixtures.productWithCode("SVN-902"))
                .getProductId();
        repository.save(preferentialRate(productId, "LONG_TERM", "Long term bonus", new BigDecimal("0.30")));
        repository.save(preferentialRate(productId, "AUTO_TRANSFER", "Auto transfer bonus", new BigDecimal("0.20")));
        repository.save(preferentialRate(otherProductId, "OTHER", "Other product bonus", new BigDecimal("0.10")));
        entityManager.flush();
        entityManager.clear();

        List<ProductPreferentialRateJpaEntity> result = repository.findAllByProductId(productId);

        assertThat(result)
                .extracting(ProductPreferentialRateJpaEntity::getConditionName)
                .containsExactly("Auto transfer bonus", "Long term bonus");
    }

    private ProductPreferentialRateJpaEntity preferentialRate(
            Long productId, String conditionCode, String conditionName, BigDecimal rate) {
        return ProductPreferentialRateJpaEntity.builder()
                .productPreferentialRateId(new ProductPreferentialRateJpaEntityId(productId, conditionCode))
                .conditionName(conditionName)
                .rate(rate)
                .build();
    }
}
