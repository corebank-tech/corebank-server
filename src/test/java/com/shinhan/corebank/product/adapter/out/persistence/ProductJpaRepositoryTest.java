package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.domain.DepositType;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.SaleStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProductJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    ProductJpaRepository repository;

    @Test
    @DisplayName("Product를 저장하면 감사 컬럼이 채워지고 findById로 조회된다")
    void saveAndFindById() {
        Product product = Product.builder()
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

        Product saved = repository.save(product);

        Product found = repository.findById(saved.getProductId()).orElseThrow();
        assertThat(found.getProductCode()).isEqualTo("SVN-001");
        assertThat(found.getProductGroup()).isEqualTo(ProductGroup.DEPOSIT);
        assertThat(found.getDepositType()).isEqualTo(DepositType.LUMP_SUM);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }
}
