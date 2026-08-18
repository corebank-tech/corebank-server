package com.shinhan.corebank.subscription.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductSubscriptionJpaRepository extends JpaRepository<ProductSubscriptionJpaEntity, Long> {
    Optional<ProductSubscriptionJpaEntity> findBySubscriptionIdAndCustomerId(Long subscriptionId, Long customerId);
}
