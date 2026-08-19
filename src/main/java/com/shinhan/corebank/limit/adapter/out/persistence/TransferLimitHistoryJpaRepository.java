package com.shinhan.corebank.limit.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferLimitHistoryJpaRepository
        extends JpaRepository<TransferLimitHistoryJpaEntity, Long> {
}
