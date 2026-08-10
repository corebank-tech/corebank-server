package com.shinhan.corebank.subscription.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTestFixtures;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SubscriptionTermsAgreementJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    SubscriptionTermsAgreementJpaRepository repository;

    @Autowired
    ProductSubscriptionJpaRepository subscriptionRepository;

    @Autowired
    ProductJpaRepository productRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("SubscriptionTermsAgreement을 저장하면 복합키(subscriptionId, termsId)로 조회된다")
    void saveAndFindById() {
        Long productId = productRepository.save(ProductTestFixtures.defaultProduct()).getProductId();
        Long customerId = SubscriptionTestFixtures.insertCustomer(jdbcTemplate, "sta_test_user");
        Long withdrawalAccountId = SubscriptionTestFixtures.insertAccount(jdbcTemplate, "110000000002", customerId, null);
        Long subscriptionId = subscriptionRepository
                .save(SubscriptionTestFixtures.defaultSubscription(customerId, productId, withdrawalAccountId))
                .getSubscriptionId();
        Long termsId = jdbcTemplate.queryForObject(
                "SELECT terms_id FROM terms WHERE terms_code = ?", Long.class, "TERMS_SERVICE");

        SubscriptionTermsAgreementJpaEntityId id = new SubscriptionTermsAgreementJpaEntityId(subscriptionId, termsId);
        SubscriptionTermsAgreementJpaEntity agreement = SubscriptionTermsAgreementJpaEntity.builder()
                .id(id)
                .termsVersion("v1.2")
                .agreedAt(LocalDateTime.now())
                .build();

        repository.save(agreement);
        entityManager.flush();
        entityManager.clear();

        SubscriptionTermsAgreementJpaEntity found = repository.findById(id).orElseThrow();
        assertThat(found.getId().getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(found.getId().getTermsId()).isEqualTo(termsId);
        assertThat(found.getTermsVersion()).isEqualTo("v1.2");
        assertThat(found.getReadAt()).isNull();
    }
}
