package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.domain.TransferSourceType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferJpaRepository extends JpaRepository<TransferJpaEntity, Long> {

    Optional<TransferJpaEntity> findByTransactionNumber(String transactionNumber);

    Optional<TransferJpaEntity> findBySourceTypeAndSourceIdAndExecutionDate(
            TransferSourceType sourceType, Long sourceId, LocalDate executionDate);
}
