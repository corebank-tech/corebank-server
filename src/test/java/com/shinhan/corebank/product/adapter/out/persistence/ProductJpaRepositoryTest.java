package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.domain.DepositType;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.SaleStatus;
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
        ProductJpaEntity saved = repository.save(ProductTestFixtures.defaultProduct());
        entityManager.flush();
        entityManager.clear();

        ProductJpaEntity found = repository.findById(saved.getProductId()).orElseThrow();
        assertThat(found.getProductCode()).isEqualTo("SVN-001");
        assertThat(found.getProductGroup()).isEqualTo(ProductGroup.DEPOSIT);
        assertThat(found.getDepositType()).isEqualTo(DepositType.LUMP_SUM);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    // REQ-PRDT-004 인수기준: "상품 테이블 DDL에 정의된 컬럼이 존재하고 시드 상품 6종 이상이
    // 등록되어 있다". 컬럼 존재는 ddl-auto: validate 가 기동 시점에 보장하므로, 여기서는
    // 시드 건수만 본다. 상품 목록(REQ-PRDT-001)은 판매중 상품만 노출하므로 ON_SALE 기준으로 센다.
    @Test
    @DisplayName("R__seed_master_data.sql이 판매중 상품을 6종 이상 채운다")
    void seedProvidesAtLeastSixOnSaleProducts() {
        entityManager.clear();

        List<ProductJpaEntity> onSale = repository.findAll().stream()
                .filter(product -> product.getSaleStatus() == SaleStatus.ON_SALE)
                .toList();

        assertThat(onSale).hasSizeGreaterThanOrEqualTo(6);
    }

    @Test
    @DisplayName("R__seed_master_data.sql로 채워진 상품이 enum 필드와 정확히 매핑된다")
    void seedProductsMapToValidEnums() {
        entityManager.clear();

        List<ProductJpaEntity> seeded = repository.findAll();

        assertThat(seeded).isNotEmpty();
        assertThat(seeded).allSatisfy(p -> {
            assertThat(p.getDepositType()).isNotNull();
            assertThat(p.getInterestPayType()).isNotNull();
            assertThat(p.getProductGroup()).isNotNull();
            assertThat(p.getSaleStatus()).isNotNull();
        });
    }
}
