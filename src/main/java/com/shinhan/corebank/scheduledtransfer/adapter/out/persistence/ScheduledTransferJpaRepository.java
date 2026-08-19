package com.shinhan.corebank.scheduledtransfer.adapter.out.persistence;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ScheduledTransferJpaRepository extends JpaRepository<ScheduledTransferJpaEntity, Long> {
    boolean existsByCustomerIdAndWithdrawalAccountIdAndPayeeAccountNumberAndAmountAndScheduledDateAndStatus(
            Long customerId, Long withdrawalAccountId, String payeeAccountNumber, Long amount,
            LocalDate scheduledDate, ScheduledTransferStatus status);
}
