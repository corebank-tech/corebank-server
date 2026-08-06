package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AutoTransferExecutionJpaRepository extends JpaRepository<AutoTransferExecutionJpaEntity, Long> {
}
