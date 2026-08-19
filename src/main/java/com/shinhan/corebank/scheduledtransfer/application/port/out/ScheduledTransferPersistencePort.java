package com.shinhan.corebank.scheduledtransfer.application.port.out;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;

import java.time.LocalDate;
import java.util.Optional;

public interface ScheduledTransferPersistencePort {
    ScheduledTransfer save(ScheduledTransfer scheduledTransfer);
    Optional<ScheduledTransfer> findById(Long scheduledTransferId);
    boolean existsActiveDuplicate(Long customerId, Long withdrawalAccountId, String payeeAccountNumber,
                                  Long amount, LocalDate scheduledDate);
}
