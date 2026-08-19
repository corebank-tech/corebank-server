package com.shinhan.corebank.autotransfer.application.port.out;

import java.time.LocalDate;
import java.util.Optional;

public interface TransferLookupPort {
    Optional<TransferLookupResult> findBySourceAndDate(Long autoTransferId, LocalDate executionDate);
}
