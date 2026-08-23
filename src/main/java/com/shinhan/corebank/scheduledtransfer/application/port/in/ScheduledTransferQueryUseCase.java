package com.shinhan.corebank.scheduledtransfer.application.port.in;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface ScheduledTransferQueryUseCase {
    Page<ScheduledTransferListItem> search(Long customerId, ScheduledTransferStatus status, Long withdrawalAccountId,
                                    LocalDate fromDate, LocalDate toDate, int page, int size, boolean all);

    // REQ-SCD-014: 처리결과 조회 - SUCCESS/FAILED/CANCELED만 대상, 목록 상단에 3종 집계 포함
    ScheduledTransferExecutionResultPage searchExecutionResults(Long customerId, Long withdrawalAccountId,
                                    LocalDate fromDate, LocalDate toDate, ScheduledTransferExecutionResultSort sort, int page, int size, boolean all);
}
