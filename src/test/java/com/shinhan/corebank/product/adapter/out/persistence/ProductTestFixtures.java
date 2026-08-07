package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.product.domain.DepositType;
import com.shinhan.corebank.product.domain.InterestPayType;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.SaleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

final class ProductTestFixtures {

    private ProductTestFixtures() {
    }

    static ProductJpaEntity defaultProduct() {
        return defaultProduct(null);
    }

    static ProductJpaEntity defaultProduct(Long productId) {
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

    static ProductJpaEntity productWithCode(String productCode) {
        return ProductJpaEntity.builder()
                .productCode(productCode)
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

    static Product defaultProductDomain() {
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
