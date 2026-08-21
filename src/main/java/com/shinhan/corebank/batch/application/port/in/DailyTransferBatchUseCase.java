package com.shinhan.corebank.batch.application.port.in;

import java.time.LocalDate;

public interface DailyTransferBatchUseCase {
    // 지정한 날짜를 기준으로 자동이체 -> 예약이체
    void run(LocalDate date);
}
