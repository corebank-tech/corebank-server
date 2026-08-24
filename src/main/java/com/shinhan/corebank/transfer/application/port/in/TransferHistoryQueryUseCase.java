package com.shinhan.corebank.transfer.application.port.in;

import java.time.LocalDate;

import com.shinhan.corebank.common.domain.ProcessResultStatus;

public interface TransferHistoryQueryUseCase {

    // REQ-TRSF-021/022/032: 출금계좌 단위 이체결과 목록 + 조회조건 전체 기준 집계
    TransferHistoryPage search(Long customerId, Long withdrawalAccountId, ProcessResultStatus status,
                                LocalDate fromDate, LocalDate toDate, TransferHistorySort sort, int page, int size, boolean all);

    // REQ-TRSF-023: 거래번호 단건 상세조회
    TransferHistoryDetail getDetail(Long customerId, String transactionNumber);
}
