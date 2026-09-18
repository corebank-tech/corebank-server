package com.shinhan.corebank.autotransfer.application.port.out;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AutoTransferExecutionHistoryQueryPort {
    Page<AutoTransferExecutionHistoryRow> search(
            Long customerId, Long withdrawalAccountId, LocalDate fromDate, LocalDate toDate, Pageable pageable);

    AutoTransferExecutionHistoryAggregate summarize(
            Long customerId, Long withdrawalAccountId, LocalDate fromDate, LocalDate toDate);

    // PROCESSING 행이 실제 있는지 확인 — 재확정 배치가 곧 정리할 건이라 "이력 없음(관리자 문의)"과 구분
    boolean existsProcessing(Long autoTransferId, LocalDate executionDate);
}
