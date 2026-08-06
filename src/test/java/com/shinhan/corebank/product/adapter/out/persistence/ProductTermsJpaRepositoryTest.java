package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
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
        Long productId = productRepository.save(ProductTestFixtures.defaultProduct()).getProductId();
        Long termsId = jdbcTemplate.queryForObject(
                "SELECT terms_id FROM terms WHERE terms_code = ?", Long.class, "TERMS_SERVICE");

        ProductTermsJpaEntityId id = new ProductTermsJpaEntityId(productId, termsId);
        ProductTermsJpaEntity productTerms = ProductTermsJpaEntity.builder()
                .id(id)
                .displayOrder((short) 1)
                .build();

        repository.save(productTerms);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findById(id)).isPresent();
    }
}
