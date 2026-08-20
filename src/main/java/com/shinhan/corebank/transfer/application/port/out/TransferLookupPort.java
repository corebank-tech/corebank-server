package com.shinhan.corebank.transfer.application.port.out;

import java.time.LocalDate;
import java.util.Optional;

import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.domain.TransferSourceType;

public interface TransferLookupPort {
    Optional<TransferResult> findBySourceAndExecutionDate(TransferSourceType sourceType, Long sourceId, LocalDate executionDate);
}
