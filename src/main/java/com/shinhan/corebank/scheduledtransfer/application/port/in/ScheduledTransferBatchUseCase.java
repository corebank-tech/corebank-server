package com.shinhan.corebank.scheduledtransfer.application.port.in;

import java.time.LocalDate;

public interface ScheduledTransferBatchUseCase {
    void executeDaily(LocalDate date);
}
