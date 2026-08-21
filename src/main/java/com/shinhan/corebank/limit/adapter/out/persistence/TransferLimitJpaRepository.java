package com.shinhan.corebank.limit.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferLimitJpaRepository extends JpaRepository<TransferLimitJpaEntity, Long> {
}
