package com.shinhan.corebank.subscription.adapter.out.persistence;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductSubscriptionJpaRepository extends JpaRepository<ProductSubscriptionJpaEntity, Long> {
    Optional<ProductSubscriptionJpaEntity> findBySubscriptionIdAndCustomerId(Long subscriptionId, Long customerId);

    boolean existsByCustomerIdAndProductIdAndStatus(Long customerId, Long productId, ProcessResultStatus status);
}
