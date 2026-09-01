package com.shinhan.corebank.autotransfer.application.port.out;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AutoTransferExecutionHistoryQueryPort {
    Page<AutoTransferExecutionHistoryRow> search(
            Long customerId, Long withdrawalAccountId, LocalDate fromDate, LocalDate toDate, Pageable pageable);

    AutoTransferExecutionHistoryAggregate summarize(
            Long customerId, Long withdrawalAccountId, LocalDate fromDate, LocalDate toDate);
}
