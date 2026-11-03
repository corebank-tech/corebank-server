package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutoTransferJpaRepository extends JpaRepository<AutoTransferJpaEntity, Long> {
    boolean existsByWithdrawalAccountIdAndDepositAccountNumberAndTransferDayAndStatus(
            Long withdrawalAccountId, String depositAccountNumber, Integer transferDay, AutoTransferStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
        SELECT a
        FROM AutoTransferJpaEntity a
        WHERE a.status = :status
          AND (
              a.withdrawalAccountId = :accountId
              OR a.depositAccountNumber = :accountNumber
          )
        ORDER BY a.autoTransferId
        """)
    List<AutoTransferJpaEntity> findLinkedForUpdate(
            @Param("accountId") Long accountId,
            @Param("accountNumber") String accountNumber,
            @Param("status") AutoTransferStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
        SELECT a
        FROM AutoTransferJpaEntity a
        WHERE a.autoTransferId = :autoTransferId
        """)
    Optional<AutoTransferJpaEntity> findByIdForUpdate(@Param("autoTransferId") Long autoTransferId);
}
