package com.shinhan.corebank.subscription.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProductLockJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    ProductLockJpaRepository repository;

    @Autowired
    ProductJpaRepository productRepository;

    @Test
    @DisplayName("findByIdForUpdate는 존재하는 product_id에 대해 값을 반환한다")
    void findByIdForUpdate_existingProduct_returnsPresent() {
        Long productId = productRepository.save(ProductTestFixtures.defaultProduct()).getProductId();

        assertThat(repository.findByIdForUpdate(productId)).isPresent();
    }

    @Test
    @DisplayName("findByIdForUpdate는 존재하지 않는 product_id에 대해 빈 값을 반환한다")
    void findByIdForUpdate_missingProduct_returnsEmpty() {
        assertThat(repository.findByIdForUpdate(999_999L)).isEmpty();
    }
}
