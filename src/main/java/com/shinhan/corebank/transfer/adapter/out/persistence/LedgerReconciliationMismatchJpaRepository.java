package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerReconciliationMismatchJpaRepository
        extends JpaRepository<LedgerReconciliationMismatchJpaEntity, Long> {

    // 엔티티를 불러오지 않고 한 문장으로 지운다. 바로 DB로 나가서 뒤이은 INSERT가 유니크 키에 걸리지 않는다.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM LedgerReconciliationMismatchJpaEntity m WHERE m.businessDate = :businessDate")
    int deleteByBusinessDate(@Param("businessDate") LocalDate businessDate);

    @Query("SELECT MAX(m.businessDate) FROM LedgerReconciliationMismatchJpaEntity m")
    Optional<LocalDate> findLatestBusinessDate();

    long countByBusinessDate(LocalDate businessDate);

    Page<LedgerReconciliationMismatchJpaEntity> findByBusinessDateOrderByAccountIdAsc(
            LocalDate businessDate, Pageable pageable);
}
