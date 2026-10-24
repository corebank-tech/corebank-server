package com.shinhan.corebank.subscription.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestPaymentJpaRepository extends JpaRepository<InterestPaymentJpaEntity, Long> {

    boolean existsByAccountId(Long accountId);
}
