package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.application.ProductSortType;
import com.shinhan.corebank.product.domain.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProductPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    ProductJpaRepository repository;

    @Autowired
    ProductRateTierJpaRepository rateTierRepository;

    @Autowired
    ProductPreferentialRateJpaRepository preferentialRateRepository;

    @Autowired
    ProductTermsJpaRepository termsRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

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

    @Test
    @DisplayName("정렬 기준(RATE)이 동률이면 productCode 오름차순으로 보조 정렬해 순서가 항상 고정된다")
    void tieBreaksByProductCode() {
        repository.save(product("SVN-202", "동률 상품 B", ProductGroup.DEPOSIT, new BigDecimal("3.30"), SaleStatus.ON_SALE));
        repository.save(product("SVN-201", "동률 상품 A", ProductGroup.DEPOSIT, new BigDecimal("3.30"), SaleStatus.ON_SALE));
        entityManager.flush();
        entityManager.clear();

        Page<Product> result = adapter.search(null, null, ProductSortType.RATE, PageRequest.of(0, 20));

        assertThat(codesInOrder(result, "SVN-2")).containsExactly("SVN-201", "SVN-202");
    }

    @Test
    @DisplayName("NAME 정렬은 상품명 오름차순으로 정렬한다")
    void sortByName() {
        repository.save(product("SVN-302", "가나다예금", ProductGroup.DEPOSIT, new BigDecimal("3.00"), SaleStatus.ON_SALE));
        repository.save(product("SVN-301", "가나다적금", ProductGroup.DEPOSIT, new BigDecimal("3.00"), SaleStatus.ON_SALE));
        entityManager.flush();
        entityManager.clear();

        Page<Product> result = adapter.search(null, null, ProductSortType.NAME, PageRequest.of(0, 20));

        assertThat(codesInOrder(result, "SVN-3")).containsExactly("SVN-302", "SVN-301");
    }

    @Test
    @DisplayName("NEW 정렬은 판매 시작일 최신순으로 정렬한다")
    void sortByNew() {
        repository.save(product("SVN-401", "신상품 A", ProductGroup.DEPOSIT, new BigDecimal("3.00"), SaleStatus.ON_SALE, LocalDate.of(2026, 1, 1)));
        repository.save(product("SVN-402", "신상품 B", ProductGroup.DEPOSIT, new BigDecimal("3.00"), SaleStatus.ON_SALE, LocalDate.of(2026, 6, 1)));
        entityManager.flush();
        entityManager.clear();

        Page<Product> result = adapter.search(null, null, ProductSortType.NEW, PageRequest.of(0, 20));

        assertThat(codesInOrder(result, "SVN-4")).containsExactly("SVN-402", "SVN-401");
    }

    @Test
    @DisplayName("존재하는 productId로 상세조회하면 product와 연관 데이터를 모두 채워서 반환한다")
    void findDetailByProductId_found() {
        ProductJpaEntity saved = repository.save(product("SVN-501", "상세조회 대상", ProductGroup.DEPOSIT, new BigDecimal("3.00"), SaleStatus.ON_SALE));
        Long productId = saved.getProductId();
        Long termsId = jdbcTemplate.queryForObject(
                "SELECT terms_id FROM terms WHERE terms_code = ?", Long.class, "TERMS_SERVICE");

        rateTierRepository.save(ProductRateTierJpaEntity.builder()
                .id(new ProductRateTierJpaEntityId(productId, (short) 12))
                .rate(new BigDecimal("3.20"))
                .build());
        preferentialRateRepository.save(ProductPreferentialRateJpaEntity.builder()
                .productPreferentialRateId(new ProductPreferentialRateJpaEntityId(productId, "AUTO_TRANSFER"))
                .conditionName("자동이체 우대")
                .rate(new BigDecimal("0.20"))
                .build());
        termsRepository.save(ProductTermsJpaEntity.builder()
                .id(new ProductTermsJpaEntityId(productId, termsId))
                .displayOrder((short) 1)
                .build());
        entityManager.flush();
        entityManager.clear();

        Optional<ProductDetail> result = adapter.findDetailByProductId(productId);

        assertThat(result).isPresent();
        ProductDetail detail = result.get();
        assertThat(detail.getProduct().getProductCode()).isEqualTo("SVN-501");
        assertThat(detail.getRateTiers()).extracting(t -> t.getId().getTermMonths()).containsExactly((short) 12);
        assertThat(detail.getPreferentialRates()).extracting(ProductPreferentialRate::getConditionName).containsExactly("자동이체 우대");
        assertThat(detail.getTerms()).extracting(t -> t.getId().getTermsId()).containsExactly(termsId);
    }

    @Test
    @DisplayName("연관 데이터가 없는 상품도 빈 목록으로 채워서 반환한다")
    void findDetailByProductId_foundWithoutChildren() {
        ProductJpaEntity saved = repository.save(product("SVN-502", "연관데이터 없음", ProductGroup.DEPOSIT, new BigDecimal("3.00"), SaleStatus.ON_SALE));

        Optional<ProductDetail> result = adapter.findDetailByProductId(saved.getProductId());

        assertThat(result).isPresent();
        assertThat(result.get().getRateTiers()).isEmpty();
        assertThat(result.get().getPreferentialRates()).isEmpty();
        assertThat(result.get().getTerms()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 productId면 빈 Optional을 반환한다")
    void findDetailByProductId_notFound() {
        Optional<ProductDetail> result = adapter.findDetailByProductId(999_999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("판매중지 상품도 상세조회는 조회된다")
    void findDetailByProductId_suspendedStillReturned() {
        ProductJpaEntity saved = repository.save(product("SVN-503", "판매중지 상품", ProductGroup.DEPOSIT, new BigDecimal("2.00"), SaleStatus.SUSPENDED));

        Optional<ProductDetail> result = adapter.findDetailByProductId(saved.getProductId());

        assertThat(result).isPresent();
        assertThat(result.get().getProduct().getSaleStatus()).isEqualTo(SaleStatus.SUSPENDED);
    }

    private static List<String> myCodesInOrder(Page<Product> page) {
        return codesInOrder(page, "SVN-1");
    }

    private static List<String> codesInOrder(Page<Product> page, String prefix) {
        return page.getContent().stream()
                .map(Product::getProductCode)
                .filter(code -> code.startsWith(prefix))
                .toList();
    }

    private ProductJpaEntity product(String code, String name, ProductGroup group, BigDecimal maxRate, SaleStatus status) {
        return product(code, name, group, maxRate, status, LocalDate.of(2026, 1, 1));
    }

    private ProductJpaEntity product(String code, String name, ProductGroup group, BigDecimal maxRate, SaleStatus status, LocalDate saleStartDate) {
        return ProductJpaEntity.builder()
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
                .saleStartDate(saleStartDate)
                .build();
    }
}
