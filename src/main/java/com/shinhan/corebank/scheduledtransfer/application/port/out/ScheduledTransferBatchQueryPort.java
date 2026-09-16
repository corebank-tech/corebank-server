package com.shinhan.corebank.scheduledtransfer.application.port.out;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import java.time.LocalDate;
import java.util.List;

public interface ScheduledTransferBatchQueryPort {
    List<ScheduledTransfer> findDueForExecution(LocalDate date);

    // PROCESSING에 멈춘 건 전체 조회 (재확정 배치 대상)
    List<ScheduledTransfer> findAllProcessing();
}
