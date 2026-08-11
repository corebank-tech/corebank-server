package com.shinhan.corebank.autotransfer.application.port.out;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;

import java.time.LocalDate;
import java.util.List;

public interface AutoTransferBatchQueryPort {
    // status = NORMAL, nextExecutionDate = date 인 자동이체를 registeredAt 오름차순으로 조회
    List<AutoTransfer> findDueForExecution(LocalDate date);
}
