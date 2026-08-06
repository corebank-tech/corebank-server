package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AutoTransferJpaRepository extends JpaRepository<AutoTransferJpaEntity, Long> {
}
