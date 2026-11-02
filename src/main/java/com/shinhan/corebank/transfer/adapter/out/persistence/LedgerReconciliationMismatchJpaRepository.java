package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LedgerReconciliationMismatchJpaRepository
        extends JpaRepository<LedgerReconciliationMismatchJpaEntity, Long> {

    boolean existsByBusinessDate(LocalDate businessDate);

    @Query("SELECT MAX(m.businessDate) FROM LedgerReconciliationMismatchJpaEntity m")
    Optional<LocalDate> findLatestBusinessDate();

    long countByBusinessDate(LocalDate businessDate);

    Page<LedgerReconciliationMismatchJpaEntity> findByBusinessDateOrderByAccountIdAsc(
            LocalDate businessDate, Pageable pageable);
}
