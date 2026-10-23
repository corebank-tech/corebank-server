package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.api.SourceExecutionKey;
import com.shinhan.corebank.transfer.domain.TransferSourceType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

public interface TransferTradeDateQueryPort {

    /** 원본 ID 가 {@code sourceIds} 중 하나이고 회차 실행일이 범위 안인 이체 행의 거래일. 거래일 NULL 행은 뺀다. */
    Map<SourceExecutionKey, LocalDate> findTradeDates(
            TransferSourceType sourceType,
            Collection<Long> sourceIds,
            LocalDate executionFromInclusive,
            LocalDate executionToInclusive);
}
