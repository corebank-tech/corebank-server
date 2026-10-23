package com.shinhan.corebank.autotransfer.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface TransferTradeDatePort {

    // 회차(executionId)가 만든 이체 행의 거래일. 이체 행이 없는 회차는 키가 없다
    Map<Long, LocalDate> findTradeDates(List<AutoTransferExecutionHistoryRow> rows);
}
