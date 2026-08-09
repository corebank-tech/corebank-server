package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferJpaRepository extends JpaRepository<TransferJpaEntity, Long> {

    Optional<TransferJpaEntity> findByTransactionNumber(String transactionNumber);
}
