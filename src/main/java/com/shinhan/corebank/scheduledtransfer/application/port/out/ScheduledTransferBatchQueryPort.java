package com.shinhan.corebank.scheduledtransfer.application.port.out;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;

import java.time.LocalDate;
import java.util.List;

public interface ScheduledTransferBatchQueryPort {
    List<ScheduledTransfer> findDueForExecution(LocalDate date);
}
