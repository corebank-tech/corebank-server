package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.application.ProductSortType;
import com.shinhan.corebank.product.domain.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProductPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    ProductJpaRepository repository;

    @Autowired
    ProductPersistenceAdapter adapter;

    @Autowired
    EntityManager entityManager;

    @BeforeEach
    void seedProducts() {
        repository.save(product("SVN-101", "정기예금 A", ProductGroup.DEPOSIT, new BigDecimal("3.00"), SaleStatus.ON_SALE));
        repository.save(product("SVN-102", "청년적금", ProductGroup.SAVINGS, new BigDecimal("4.50"), SaleStatus.ON_SALE));
        repository.save(product("SVN-103", "정기예금 B(판매중지)", ProductGroup.DEPOSIT, new BigDecimal("5.00"), SaleStatus.SUSPENDED));
        repository.save(product("SVN-104", "특판예금", ProductGroup.DEPOSIT, new BigDecimal("2.00"), SaleStatus.ON_SALE));
        entityManager.flush();
        entityManager.clear();
    }

    // R__seed_master_data.sql이 채운 상품(PRD_YOUTH_SAVE, PRD_BASIC_DEP)이 항상 같이 존재하므로,
    // 전체 개수를 하드코딩하지 않고 이번 테스트가 만든 코드("SVN-1"로 시작)만 걸러서 검증한다.

    @Test
    @DisplayName("productGroup으로 필터링하고, 판매중지 상품은 제외하고, 최고금리 내림차순으로 정렬한다")
    void filterByGroupExcludeSuspendedSortByRate() {
        Page<Product> result = adapter.search(ProductGroup.DEPOSIT, null, ProductSortType.RATE, PageRequest.of(0, 10));

        assertThat(myCodesInOrder(result)).containsExactly("SVN-101", "SVN-104");
    }

    @Test
    @DisplayName("keyword가 상품명에 부분일치하는 상품만 조회한다")
    void filterByKeyword() {
        Page<Product> result = adapter.search(null, "적금", ProductSortType.RATE, PageRequest.of(0, 10));

        assertThat(myCodesInOrder(result)).containsExactly("SVN-102");
    }

    @Test
    @DisplayName("조건 없이 조회하면 판매중지 상품만 빠지고, 페이징이 적용된다")
    void pagingWithoutFilter() {
        long expectedTotal = repository.findAll().stream()
                .filter(p -> p.getSaleStatus() == SaleStatus.ON_SALE)
                .count();

        Page<Product> firstPage = adapter.search(null, null, ProductSortType.NAME, PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(expectedTotal);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(myCodesInOrder(firstPage)).doesNotContain("SVN-103");
    }

    private static List<String> myCodesInOrder(Page<Product> page) {
        return page.getContent().stream()
                .map(Product::getProductCode)
                .filter(code -> code.startsWith("SVN-1"))
                .toList();
    }

    private Product product(String code, String name, ProductGroup group, BigDecimal maxRate, SaleStatus status) {
        return Product.builder()
                .productCode(code)
                .productName(name)
                .productGroup(group)
                .depositType(DepositType.LUMP_SUM)
                .baseRate(maxRate.subtract(new BigDecimal("0.50")))
                .maxRate(maxRate)
                .minAmount(100_000L)
                .maxAmount(100_000_000L)
                .amountUnit(10_000L)
                .minTermMonths((short) 6)
                .maxTermMonths((short) 36)
                .interestPayType(InterestPayType.SIMPLE)
                .saleStatus(status)
                .newFlag(false)
                .singleAccountLimit(false)
                .build();
    }
}
