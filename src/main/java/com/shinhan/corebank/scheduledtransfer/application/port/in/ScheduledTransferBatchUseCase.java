package com.shinhan.corebank.scheduledtransfer.application.port.in;

import java.time.LocalDate;

public interface ScheduledTransferBatchUseCase {
    void executeDaily(LocalDate date);

    // PROCESSING에 멈춘 건 전체를 찾아 실제 거래 여부를 확인하고 SUCCESS/FAILED로 확정
    void reconcileStuckExecutions(LocalDate date);
}
