package com.shinhan.corebank.batch.adapter.in.scheduler;

import com.shinhan.corebank.batch.application.port.in.DailyTransferBatchUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyTransferBatchScheduler {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final DailyTransferBatchUseCase dailyTransferBatchUseCase;
    private final Clock clock;

    // 자정 00시 10분 후 자동이체 -> 예약이체 순으로 배치 시작
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void runDailyBatch() {
        dailyTransferBatchUseCase.run(LocalDate.now(clock.withZone(SEOUL)));
    }
}
