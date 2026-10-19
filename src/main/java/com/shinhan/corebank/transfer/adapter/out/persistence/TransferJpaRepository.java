package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.domain.TransferSourceType;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferJpaRepository extends JpaRepository<TransferJpaEntity, Long> {

    Optional<TransferJpaEntity> findByTransactionNumber(String transactionNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransferJpaEntity t WHERE t.transactionNumber = :transactionNumber")
    Optional<TransferJpaEntity> findByTransactionNumberForUpdate(@Param("transactionNumber") String transactionNumber);

    // ix_transfer_status_created를 탄다. 당행 TIMEOUT은 없지만 은행코드로 한 번 더 거른다.
    @Query(
            """
        SELECT t FROM TransferJpaEntity t
        WHERE t.status = com.shinhan.corebank.common.domain.ProcessResultStatus.TIMEOUT
          AND t.payeeBankCode IS NOT NULL
          AND t.createdAt < :createdBefore
        ORDER BY t.createdAt
        """)
    List<TransferJpaEntity> findUnsettledInterbank(
            @Param("createdBefore") LocalDateTime createdBefore, Pageable pageable);

    Optional<TransferJpaEntity> findBySourceTypeAndSourceIdAndExecutionDate(
            TransferSourceType sourceType, Long sourceId, LocalDate executionDate);
}
