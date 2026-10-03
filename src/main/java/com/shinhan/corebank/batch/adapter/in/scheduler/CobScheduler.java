package com.shinhan.corebank.batch.adapter.in.scheduler;

import com.shinhan.corebank.batch.application.port.in.CobRunnerUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CobScheduler {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final CobRunnerUseCase cobRunnerUseCase;
    private final Clock clock;

    // COB 23:30 (ADR-0004) - 자동이체 배치(00:10)보다 먼저 돌아야 영업일이 넘어가 있다
    @Scheduled(cron = "0 30 23 * * *", zone = "Asia/Seoul")
    public void runCob() {
        cobRunnerUseCase.run(LocalDate.now(clock.withZone(SEOUL)));
    }
}
