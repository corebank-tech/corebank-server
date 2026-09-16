package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProductTermsJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    ProductJpaRepository productRepository;

    @Autowired
    ProductTermsJpaRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("ProductTerms를 저장하면 복합키로 조회된다")
    void saveAndFindById() {
        Long productId =
                productRepository.save(ProductTestFixtures.defaultProduct()).getProductId();
        Long termsId = jdbcTemplate.queryForObject(
                "SELECT terms_id FROM terms WHERE terms_code = ?", Long.class, "TERMS_SERVICE");

        ProductTermsJpaEntityId id = new ProductTermsJpaEntityId(productId, termsId);
        ProductTermsJpaEntity productTerms =
                ProductTermsJpaEntity.builder().id(id).displayOrder((short) 1).build();

        repository.save(productTerms);
        entityManager.flush();
        entityManager.clear();

        ProductTermsJpaEntity found = repository.findById(id).orElseThrow();
        assertThat(found.getDisplayOrder()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("productId로 조회하면 다른 상품 것은 제외하고 displayOrder 오름차순으로 반환한다")
    void findAllByProductId_returnsOwnTermsSortedByDisplayOrder() {
        Long productId =
                productRepository.save(ProductTestFixtures.defaultProduct()).getProductId();
        Long otherProductId = productRepository
                .save(ProductTestFixtures.productWithCode("SVN-903"))
                .getProductId();
        Long serviceTermsId = jdbcTemplate.queryForObject(
                "SELECT terms_id FROM terms WHERE terms_code = ?", Long.class, "TERMS_SERVICE");
        Long privacyTermsId = jdbcTemplate.queryForObject(
                "SELECT terms_id FROM terms WHERE terms_code = ?", Long.class, "TERMS_PRIVACY");

        repository.save(productTerms(productId, privacyTermsId, (short) 2));
        repository.save(productTerms(productId, serviceTermsId, (short) 1));
        repository.save(productTerms(otherProductId, serviceTermsId, (short) 1));
        entityManager.flush();
        entityManager.clear();

        List<ProductTermsJpaEntity> result = repository.findAllByProductId(productId);

        assertThat(result).extracting(e -> e.getId().getTermsId()).containsExactly(serviceTermsId, privacyTermsId);
    }

    private ProductTermsJpaEntity productTerms(Long productId, Long termsId, short displayOrder) {
        return ProductTermsJpaEntity.builder()
                .id(new ProductTermsJpaEntityId(productId, termsId))
                .displayOrder(displayOrder)
                .build();
    }
}
