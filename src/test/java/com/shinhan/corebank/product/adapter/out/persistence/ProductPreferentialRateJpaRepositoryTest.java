package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.domain.DepositType;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.ProductPreferentialRate;
import com.shinhan.corebank.product.domain.ProductPreferentialRateId;
import com.shinhan.corebank.product.domain.SaleStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProductPreferentialRateJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    ProductJpaRepository productRepository;

    @Autowired
    ProductPreferentialRateJpaRepository repository;

    Long productId;

    @BeforeEach
    void setUp() {
        productId = productRepository.save(defaultProduct()).getProductId();
    }

    @Test
    @DisplayName("ProductPreferentialRate를 저장하면 복합키로 조회된다")
    void saveAndFindById() {
        ProductPreferentialRateId id = new ProductPreferentialRateId(productId, "LONG_TERM");
        ProductPreferentialRate preferentialRate = ProductPreferentialRate.builder()
                .productPreferentialRateId(id)
                .conditionName("장기거래 우대")
                .rate(new BigDecimal("0.30"))
                .build();

        repository.save(preferentialRate);

        ProductPreferentialRate found = repository.findById(id).orElseThrow();
        assertThat(found.getConditionName()).isEqualTo("장기거래 우대");
        assertThat(found.getRate()).isEqualByComparingTo("0.30");
    }

    private Product defaultProduct() {
        return Product.builder()
                .productCode("SVN-001")
                .productName("정기예금")
                .productGroup(ProductGroup.DEPOSIT)
                .depositType(DepositType.LUMP_SUM)
                .baseRate(new BigDecimal("2.50"))
                .maxRate(new BigDecimal("3.00"))
                .minAmount(100_000L)
                .maxAmount(100_000_000L)
                .amountUnit(10_000L)
                .minTermMonths((short) 6)
                .maxTermMonths((short) 36)
                .interestPayType("MATURITY")
                .saleStatus(SaleStatus.ON_SALE)
                .newFlag(false)
                .singleAccountLimit(false)
                .build();
    }
}
