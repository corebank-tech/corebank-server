package com.shinhan.corebank.autotransfer.application.port.out;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface AutoTransferExecutionHistoryQueryPort {
    Page<AutoTransferExecutionHistoryRow> search(Long customerId, Long withdrawalAccountId, LocalDate fromDate,
                                                 LocalDate toDate, Pageable pageable);
    AutoTransferExecutionHistoryAggregate summarize(Long customerId, Long withdrawalAccountId, LocalDate fromDate, LocalDate toDate);
}
