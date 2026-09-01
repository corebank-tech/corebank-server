package com.shinhan.corebank.subscription.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTestFixtures;
import jakarta.persistence.EntityManager;
import java.util.List;
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
    @DisplayName("ProductSubscription을 저장하면 자동 채번된 subscriptionId로 전체 필드가 보존된 채 조회된다")
    void saveAndFindById() {
        Long productId =
                productRepository.save(ProductTestFixtures.defaultProduct()).getProductId();
        Long customerId = SubscriptionTestFixtures.insertCustomer(jdbcTemplate, "sub_test_user");
        Long withdrawalAccountId =
                SubscriptionTestFixtures.insertAccount(jdbcTemplate, "110000000001", customerId, null);

        ProductSubscriptionJpaEntity subscription =
                SubscriptionTestFixtures.defaultSubscription(customerId, productId, withdrawalAccountId);

        Long subscriptionId = repository.save(subscription).getSubscriptionId();
        entityManager.flush();
        entityManager.clear();

        ProductSubscriptionJpaEntity found = repository.findById(subscriptionId).orElseThrow();
        assertThat(found.getCustomerId()).isEqualTo(customerId);
        assertThat(found.getProductId()).isEqualTo(productId);
        assertThat(found.getAccountId()).isNull();
        assertThat(found.getWithdrawalAccountId()).isEqualTo(withdrawalAccountId);
        assertThat(found.getSubscriptionAmount()).isEqualTo(subscription.getSubscriptionAmount());
        assertThat(found.getTermMonths()).isEqualTo(subscription.getTermMonths());
        assertThat(found.getPaymentDay()).isEqualTo(subscription.getPaymentDay());
        assertThat(found.getBaseRate()).isEqualByComparingTo(subscription.getBaseRate());
        assertThat(found.getPreferentialRate()).isEqualByComparingTo(subscription.getPreferentialRate());
        assertThat(found.getAppliedRate()).isEqualByComparingTo(subscription.getAppliedRate());
        assertThat(found.getMaturityHandling()).isEqualTo(subscription.getMaturityHandling());
        assertThat(found.getExpectedMaturityAmount()).isEqualTo(subscription.getExpectedMaturityAmount());
        assertThat(found.getStatus()).isEqualTo(subscription.getStatus());
        assertThat(found.getTransactionNumber()).isEqualTo(subscription.getTransactionNumber());
        assertThat(found.getOpenedDate()).isEqualTo(subscription.getOpenedDate());
        assertThat(found.getMaturityDate()).isEqualTo(subscription.getMaturityDate());
        assertThat(found.getSubscribedAt()).isEqualTo(subscription.getSubscribedAt());
    }

    @Test
    @DisplayName("findBySubscriptionIdAndCustomerId는 소유자가 일치할 때만 값을 반환한다")
    void findBySubscriptionIdAndCustomerId_ownershipScoped() {
        Long productId =
                productRepository.save(ProductTestFixtures.defaultProduct()).getProductId();
        Long customerId = SubscriptionTestFixtures.insertCustomer(jdbcTemplate, "sub_own_test");
        Long withdrawalAccountId =
                SubscriptionTestFixtures.insertAccount(jdbcTemplate, "110000000003", customerId, null);
        Long subscriptionId = repository
                .save(SubscriptionTestFixtures.defaultSubscription(customerId, productId, withdrawalAccountId))
                .getSubscriptionId();

        assertThat(repository.findBySubscriptionIdAndCustomerId(subscriptionId, customerId))
                .isPresent();
        assertThat(repository.findBySubscriptionIdAndCustomerId(subscriptionId, 999_999L))
                .isEmpty();
        assertThat(repository.findBySubscriptionIdAndCustomerId(999_999L, customerId))
                .isEmpty();
    }

    @Test
    @DisplayName("findAllByCustomerIdAndProductIdForUpdate는 customerId+productId 조합이 일치하는 행만 반환한다")
    void findAllByCustomerIdAndProductIdForUpdate_returnsOnlyMatchingCombination() {
        Long productId =
                productRepository.save(ProductTestFixtures.defaultProduct()).getProductId();
        Long otherProductId = productRepository
                .save(ProductTestFixtures.productWithCode("SVN-002"))
                .getProductId();
        Long customerId = SubscriptionTestFixtures.insertCustomer(jdbcTemplate, "sub_lock_test");
        Long withdrawalAccountId =
                SubscriptionTestFixtures.insertAccount(jdbcTemplate, "110000000005", customerId, null);

        repository.save(SubscriptionTestFixtures.defaultSubscription(customerId, productId, withdrawalAccountId));
        repository.save(SubscriptionTestFixtures.defaultSubscription(customerId, otherProductId, withdrawalAccountId));

        List<ProductSubscriptionJpaEntity> found =
                repository.findAllByCustomerIdAndProductIdForUpdate(customerId, productId);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getProductId()).isEqualTo(productId);
    }
}
