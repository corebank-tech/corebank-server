package com.shinhan.corebank.autotransfer.application.port.out;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AutoTransferExecutionHistoryQueryPort {
    Page<AutoTransferExecutionHistoryRow> search(
            Long customerId, Long withdrawalAccountId, LocalDate fromDate, LocalDate toDate, Pageable pageable);

    AutoTransferExecutionHistoryAggregate summarize(
            Long customerId, Long withdrawalAccountId, LocalDate fromDate, LocalDate toDate);

    // status=NORMAL이면서 nextExecutionDate가 before보다 과거인 자동이체만 DB에서 걸러서 반환한다.
    // count 쿼리가 딸린 Page가 아니라 List라 쿼리 1번으로 끝난다(#442 리뷰 반영)
    List<AutoTransfer> findNormalStuckBefore(Long customerId, Long withdrawalAccountId, LocalDate before);

    // 주어진 자동이체들 중 PROCESSING 행이 있는 것만 한 번에 골라 반환한다 — 재확정 배치가 곧
    // 정리할 건이라 "이력 없음(관리자 문의)"과 구분해야 한다. 후보마다 개별 조회하면 N+1이 되므로
    // ID 목록을 한 번에 묶어서 조회한다(#442 리뷰 반영)
    Set<Long> findProcessingAutoTransferIds(List<Long> autoTransferIds);
}
