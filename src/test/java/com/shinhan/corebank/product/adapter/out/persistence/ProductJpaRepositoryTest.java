package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.domain.DepositType;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductGroup;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProductJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    ProductJpaRepository repository;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("Product를 저장하면 감사 컬럼이 채워지고 findById로 조회된다")
    void saveAndFindById() {
        Product saved = repository.save(ProductTestFixtures.defaultProduct());
        entityManager.flush();
        entityManager.clear();

        Product found = repository.findById(saved.getProductId()).orElseThrow();
        assertThat(found.getProductCode()).isEqualTo("SVN-001");
        assertThat(found.getProductGroup()).isEqualTo(ProductGroup.DEPOSIT);
        assertThat(found.getDepositType()).isEqualTo(DepositType.LUMP_SUM);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("R__seed_master_data.sql로 채워진 상품이 enum 필드와 정확히 매핑된다")
    void seedProductsMapToValidEnums() {
        entityManager.clear();

        List<Product> seeded = repository.findAll();

        assertThat(seeded).isNotEmpty();
        assertThat(seeded).allSatisfy(p -> {
            assertThat(p.getDepositType()).isNotNull();
            assertThat(p.getInterestPayType()).isNotNull();
            assertThat(p.getProductGroup()).isNotNull();
            assertThat(p.getSaleStatus()).isNotNull();
        });
    }
}
