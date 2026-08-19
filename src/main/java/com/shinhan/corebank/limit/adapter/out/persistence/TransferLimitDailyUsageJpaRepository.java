package com.shinhan.corebank.limit.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferLimitDailyUsageJpaRepository
        extends JpaRepository<TransferLimitDailyUsageJpaEntity, TransferLimitDailyUsageId> {
}
