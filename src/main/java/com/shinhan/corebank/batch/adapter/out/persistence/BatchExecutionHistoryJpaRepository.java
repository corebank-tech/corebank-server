package com.shinhan.corebank.batch.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchExecutionHistoryJpaRepository extends JpaRepository<BatchExecutionHistoryJpaEntity, Long> {}
