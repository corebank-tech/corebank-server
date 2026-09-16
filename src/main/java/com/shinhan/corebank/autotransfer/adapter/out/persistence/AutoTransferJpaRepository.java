package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutoTransferJpaRepository extends JpaRepository<AutoTransferJpaEntity, Long> {
    boolean existsByWithdrawalAccountIdAndDepositAccountNumberAndTransferDayAndStatus(
            Long withdrawalAccountId, String depositAccountNumber, Integer transferDay, AutoTransferStatus status);
}
