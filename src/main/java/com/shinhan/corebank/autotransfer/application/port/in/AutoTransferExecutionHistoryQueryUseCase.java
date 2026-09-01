package com.shinhan.corebank.autotransfer.application.port.in;

import java.time.LocalDate;

// Controller가 실행 이력을 호출할 때 호출하는 진입점
public interface AutoTransferExecutionHistoryQueryUseCase {
    AutoTransferExecutionHistoryResult search(
            Long customerId,
            Long withdrawalAccountId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            boolean all);
}
