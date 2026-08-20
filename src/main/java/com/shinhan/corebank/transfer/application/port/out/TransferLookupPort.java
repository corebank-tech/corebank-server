package com.shinhan.corebank.transfer.application.port.out;

import java.time.LocalDate;
import java.util.Optional;

import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferSourceType;

public interface TransferLookupPort {
    Optional<TransferResult> findBySourceAndExecutionDate(TransferSourceType sourceType, Long sourceId, LocalDate executionDate);

    // REQ-TRSF-023: 상세조회용 거래번호 단건 조회. 소유권 검증은 호출자(서비스)가 수행한다.
    Optional<Transfer> findByTransactionNumber(String transactionNumber);
}
