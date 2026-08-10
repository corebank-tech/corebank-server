package com.shinhan.corebank.subscription.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTestFixtures;
import com.shinhan.corebank.subscription.domain.MaturityHandling;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProductSubscriptionJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    ProductSubscriptionJpaRepository repository;

    @Autowired
    ProductJpaRepository productRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("ProductSubscription을 저장하면 자동 채번된 subscriptionId로 조회된다")
    void saveAndFindById() {
        Long productId = productRepository.save(ProductTestFixtures.defaultProduct()).getProductId();
        Long customerId = SubscriptionTestFixtures.insertCustomer(jdbcTemplate, "sub_test_user");
        Long withdrawalAccountId = SubscriptionTestFixtures.insertAccount(jdbcTemplate, "110000000001", customerId, null);

        ProductSubscriptionJpaEntity subscription = ProductSubscriptionJpaEntity.builder()
                .customerId(customerId)
                .productId(productId)
                .withdrawalAccountId(withdrawalAccountId)
                .subscriptionAmount(1_000_000L)
                .termMonths((short) 12)
                .baseRate(new BigDecimal("2.50"))
                .preferentialRate(new BigDecimal("0.30"))
                .appliedRate(new BigDecimal("2.80"))
                .maturityHandling(MaturityHandling.TRANSFER)
                .status(ProcessResultStatus.SUCCESS)
                .subscribedAt(LocalDateTime.now())
                .build();

        Long subscriptionId = repository.save(subscription).getSubscriptionId();
        entityManager.flush();
        entityManager.clear();

        ProductSubscriptionJpaEntity found = repository.findById(subscriptionId).orElseThrow();
        assertThat(found.getCustomerId()).isEqualTo(customerId);
        assertThat(found.getStatus()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(found.getMaturityHandling()).isEqualTo(MaturityHandling.TRANSFER);
    }
}