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

    @Test
    @DisplayName("productId로 조회하면 다른 상품 것은 제외하고 termMonths 오름차순으로 반환한다")
    void findAllByProductId_returnsOwnTiersSortedByTermMonths() {
        Long otherProductId = productRepository.save(ProductTestFixtures.productWithCode("SVN-901")).getProductId();
        repository.save(rateTier(productId, (short) 24, new BigDecimal("3.50")));
        repository.save(rateTier(productId, (short) 6, new BigDecimal("2.80")));
        repository.save(rateTier(otherProductId, (short) 12, new BigDecimal("3.00")));
        entityManager.flush();
        entityManager.clear();

        List<ProductRateTierJpaEntity> result = repository.findAllByProductId(productId);

        assertThat(result).extracting(e -> e.getId().getTermMonths())
                .containsExactly((short) 6, (short) 24);
    }

    private ProductRateTierJpaEntity rateTier(Long productId, short termMonths, BigDecimal rate) {
        return ProductRateTierJpaEntity.builder()
                .id(new ProductRateTierJpaEntityId(productId, termMonths))
                .rate(rate)
                .build();
    }
}
