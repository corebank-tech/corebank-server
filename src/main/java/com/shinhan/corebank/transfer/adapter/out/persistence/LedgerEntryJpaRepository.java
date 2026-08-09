package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryJpaEntity, LedgerEntryId> {

    List<LedgerEntryJpaEntity> findByTransactionNumber(String transactionNumber);
}
