package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.product.domain.DepositType;
import com.shinhan.corebank.product.domain.InterestPayType;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.SaleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * product 도메인 테스트 전반에서 재사용하는 공용 픽스처(Object Mother).
 * 다른 패키지(예: adapter.in.web 테스트)에서도 쓸 수 있도록 public으로 공개한다.
 */
public final class ProductTestFixtures {

    private ProductTestFixtures() {
    }

    public static ProductJpaEntity defaultProduct() {
        return defaultProduct(null);
    }

    public static ProductJpaEntity defaultProduct(Long productId) {
        return ProductJpaEntity.builder()
                .productId(productId)
                .productCode("SVN-001")
                .productName("정기예금")
                .productGroup(ProductGroup.DEPOSIT)
                .depositType(DepositType.LUMP_SUM)
                .summary("6개월 이상 정기예금 상품")
                .description("표준 정기예금 상품 설명")
                .baseRate(new BigDecimal("2.50"))
                .maxRate(new BigDecimal("3.00"))
                .minAmount(100_000L)
                .maxAmount(100_000_000L)
                .amountUnit(10_000L)
                .minTermMonths((short) 6)
                .maxTermMonths((short) 36)
                .interestPayType(InterestPayType.SIMPLE)
                .saleStatus(SaleStatus.ON_SALE)
                .saleStartDate(LocalDate.of(2026, 1, 1))
                .saleEndDate(LocalDate.of(2026, 12, 31))
                .newFlag(false)
                .singleAccountLimit(false)
                .build();
    }

    public static ProductJpaEntity productWithCode(String productCode) {
        return productWithCode(productCode, SaleStatus.ON_SALE);
    }

    public static ProductJpaEntity productWithCode(String productCode, SaleStatus saleStatus) {
        return productWithCodeAndName(productCode, "정기예금", saleStatus);
    }

    public static ProductJpaEntity productWithCode(String productCode, String productName) {
        return productWithCodeAndName(productCode, productName, SaleStatus.ON_SALE);
    }

    private static ProductJpaEntity productWithCodeAndName(String productCode, String productName, SaleStatus saleStatus) {
        return ProductJpaEntity.builder()
                .productCode(productCode)
                .productName(productName)
                .productGroup(ProductGroup.DEPOSIT)
                .depositType(DepositType.LUMP_SUM)
                .summary("6개월 이상 정기예금 상품")
                .description("표준 정기예금 상품 설명")
                .baseRate(new BigDecimal("2.50"))
                .maxRate(new BigDecimal("3.00"))
                .minAmount(100_000L)
                .maxAmount(100_000_000L)
                .amountUnit(10_000L)
                .minTermMonths((short) 6)
                .maxTermMonths((short) 36)
                .interestPayType(InterestPayType.SIMPLE)
                .saleStatus(saleStatus)
                .saleStartDate(LocalDate.of(2026, 1, 1))
                .saleEndDate(LocalDate.of(2026, 12, 31))
                .newFlag(false)
                .singleAccountLimit(false)
                .build();
    }

    public static Product defaultProductDomain() {
        return Product.builder()
                .productId(1L)
                .productCode("SVN-001")
                .productName("정기예금")
                .productGroup(ProductGroup.DEPOSIT)
                .depositType(DepositType.LUMP_SUM)
                .summary("6개월 이상 정기예금 상품")
                .description("표준 정기예금 상품 설명")
                .baseRate(new BigDecimal("2.50"))
                .maxRate(new BigDecimal("3.00"))
                .minAmount(100_000L)
                .maxAmount(100_000_000L)
                .amountUnit(10_000L)
                .minTermMonths((short) 6)
                .maxTermMonths((short) 36)
                .interestPayType(InterestPayType.SIMPLE)
                .saleStatus(SaleStatus.ON_SALE)
                .saleStartDate(LocalDate.of(2026, 1, 1))
                .saleEndDate(LocalDate.of(2026, 12, 31))
                .newFlag(false)
                .singleAccountLimit(false)
                .build();
    }
}
