package com.shinhan.corebank.batch.application.port.in;

import java.time.LocalDate;

public interface CobRunnerUseCase {
    void run(LocalDate calendarDate);
}
