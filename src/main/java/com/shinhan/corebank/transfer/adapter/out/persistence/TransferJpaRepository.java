package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.domain.TransferSourceType;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferJpaRepository extends JpaRepository<TransferJpaEntity, Long> {

    Optional<TransferJpaEntity> findByTransactionNumber(String transactionNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransferJpaEntity t WHERE t.transactionNumber = :transactionNumber")
    Optional<TransferJpaEntity> findByTransactionNumberForUpdate(@Param("transactionNumber") String transactionNumber);

    Optional<TransferJpaEntity> findBySourceTypeAndSourceIdAndExecutionDate(
            TransferSourceType sourceType, Long sourceId, LocalDate executionDate);
}
