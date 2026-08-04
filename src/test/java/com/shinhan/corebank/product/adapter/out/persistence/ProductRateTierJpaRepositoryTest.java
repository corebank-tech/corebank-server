package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.domain.DepositType;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.ProductRateTier;
import com.shinhan.corebank.product.domain.ProductRateTierId;
import com.shinhan.corebank.product.domain.SaleStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProductRateTierJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    ProductJpaRepository productRepository;

    @Autowired
    ProductRateTierJpaRepository repository;

    Long productId;

    @BeforeEach
    void setUp() {
        productId = productRepository.save(defaultProduct()).getProductId();
    }

    @Test
    @DisplayName("ProductRateTier를 저장하면 복합키로 조회된다")
    void saveAndFindById() {
        ProductRateTierId id = new ProductRateTierId(productId, (short) 12);
        ProductRateTier tier = ProductRateTier.builder()
                .id(id)
                .rate(new BigDecimal("2.80"))
                .build();

        repository.save(tier);

        ProductRateTier found = repository.findById(id).orElseThrow();
        assertThat(found.getRate()).isEqualByComparingTo("2.80");
    }

    private Product defaultProduct() {
        return Product.builder()
                .productCode("SVN-001")
                .productName("정기예금")
                .productGroup(ProductGroup.DEPOSIT)
                .depositType(DepositType.거치식)
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
