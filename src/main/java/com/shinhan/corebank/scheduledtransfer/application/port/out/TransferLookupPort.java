package com.shinhan.corebank.scheduledtransfer.application.port.out;

import java.time.LocalDate;
import java.util.Optional;

public interface TransferLookupPort {
    Optional<TransferLookupResult> findBySourceAndDate(Long scheduledTransferId, LocalDate executionDate);
}
