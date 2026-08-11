package com.shinhan.corebank.subscription.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.subscription.domain.ProductSubscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductSubscriptionMapperTest {

    @Test
    @DisplayName("JPA 엔티티를 도메인 객체로 변환하면 모든 필드가 보존된다")
    void toDomain_preservesAllFields() {
        ProductSubscriptionJpaEntity entity = SubscriptionTestFixtures.defaultSubscription(2L, 3L, 5L);
        ReflectionTestUtils.setField(entity, "subscriptionId", 1L);
        ReflectionTestUtils.setField(entity, "accountId", 4L);

        ProductSubscription domain = ProductSubscriptionMapper.toDomain(entity);

        assertThat(domain.getSubscriptionId()).isEqualTo(entity.getSubscriptionId());
        assertThat(domain.getCustomerId()).isEqualTo(entity.getCustomerId());
        assertThat(domain.getProductId()).isEqualTo(entity.getProductId());
        assertThat(domain.getAccountId()).isEqualTo(entity.getAccountId());
        assertThat(domain.getWithdrawalAccountId()).isEqualTo(entity.getWithdrawalAccountId());
        assertThat(domain.getSubscriptionAmount()).isEqualTo(entity.getSubscriptionAmount());
        assertThat(domain.getTermMonths()).isEqualTo(entity.getTermMonths());
        assertThat(domain.getPaymentDay()).isEqualTo(entity.getPaymentDay());
        assertThat(domain.getBaseRate()).isEqualByComparingTo(entity.getBaseRate());
        assertThat(domain.getPreferentialRate()).isEqualByComparingTo(entity.getPreferentialRate());
        assertThat(domain.getAppliedRate()).isEqualByComparingTo(entity.getAppliedRate());
        assertThat(domain.getMaturityHandling()).isEqualTo(entity.getMaturityHandling());
        assertThat(domain.getExpectedMaturityAmount()).isEqualTo(entity.getExpectedMaturityAmount());
        assertThat(domain.getStatus()).isEqualTo(entity.getStatus());
        assertThat(domain.getTransactionNumber()).isEqualTo(entity.getTransactionNumber());
        assertThat(domain.getOpenedDate()).isEqualTo(entity.getOpenedDate());
        assertThat(domain.getMaturityDate()).isEqualTo(entity.getMaturityDate());
        assertThat(domain.getSubscribedAt()).isEqualTo(entity.getSubscribedAt());
    }

    @Test
    @DisplayName("도메인 객체를 JPA 엔티티로 변환하면 모든 필드가 보존된다")
    void toEntity_preservesAllFields() {
        ProductSubscription domain = SubscriptionTestFixtures.defaultSubscriptionDomain();

        ProductSubscriptionJpaEntity entity = ProductSubscriptionMapper.toEntity(domain);

        assertThat(entity.getSubscriptionId()).isEqualTo(domain.getSubscriptionId());
        assertThat(entity.getCustomerId()).isEqualTo(domain.getCustomerId());
        assertThat(entity.getProductId()).isEqualTo(domain.getProductId());
        assertThat(entity.getAccountId()).isEqualTo(domain.getAccountId());
        assertThat(entity.getWithdrawalAccountId()).isEqualTo(domain.getWithdrawalAccountId());
        assertThat(entity.getSubscriptionAmount()).isEqualTo(domain.getSubscriptionAmount());
        assertThat(entity.getTermMonths()).isEqualTo(domain.getTermMonths());
        assertThat(entity.getPaymentDay()).isEqualTo(domain.getPaymentDay());
        assertThat(entity.getBaseRate()).isEqualByComparingTo(domain.getBaseRate());
        assertThat(entity.getPreferentialRate()).isEqualByComparingTo(domain.getPreferentialRate());
        assertThat(entity.getAppliedRate()).isEqualByComparingTo(domain.getAppliedRate());
        assertThat(entity.getMaturityHandling()).isEqualTo(domain.getMaturityHandling());
        assertThat(entity.getExpectedMaturityAmount()).isEqualTo(domain.getExpectedMaturityAmount());
        assertThat(entity.getStatus()).isEqualTo(domain.getStatus());
        assertThat(entity.getTransactionNumber()).isEqualTo(domain.getTransactionNumber());
        assertThat(entity.getOpenedDate()).isEqualTo(domain.getOpenedDate());
        assertThat(entity.getMaturityDate()).isEqualTo(domain.getMaturityDate());
        assertThat(entity.getSubscribedAt()).isEqualTo(domain.getSubscribedAt());
    }
}
