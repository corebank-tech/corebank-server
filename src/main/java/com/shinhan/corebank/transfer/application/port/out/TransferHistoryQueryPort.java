package com.shinhan.corebank.transfer.application.port.out;

import java.time.LocalDate;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.in.TransferHistorySort;
import com.shinhan.corebank.transfer.domain.Transfer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransferHistoryQueryPort {

    // 소유권 검증은 서비스가 이미 마쳤다는 전제 하에 withdrawalAccountId로만 필터링한다
    Page<Transfer> search(Long withdrawalAccountId, ProcessResultStatus status, LocalDate fromDate, LocalDate toDate,
                          TransferHistorySort sort, Pageable pageable);

    // 페이지 합계가 아니라 조회조건(status 필터 포함) 전체 기준 집계
    TransferHistoryAggregate summarize(Long withdrawalAccountId, ProcessResultStatus status, LocalDate fromDate, LocalDate toDate);
}
