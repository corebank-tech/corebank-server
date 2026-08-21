package com.shinhan.corebank.batch.adapter.in.scheduler;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferBatchUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferBatchUseCase;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class DailyTransferBatchScheduler {
    // 자정 00시 10분 후 자동이체 -> 예약이체 순으로 배치 시작
    private static final Logger log = LoggerFactory.getLogger(DailyTransferBatchScheduler.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final AutoTransferBatchUseCase autoTransferBatchUseCase;
    private final ScheduledTransferBatchUseCase scheduledTransferBatchUseCase;
    private final Clock clock;

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void runDailyBatch() {
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        log.info("자동이체 배치 시작 - date={}", today);
        autoTransferBatchUseCase.executeDaily(today);
        log.info("자동이체 배치 종료 - date={}", today);

        log.info("재확정 배치 시작 - date={}", today);
        autoTransferBatchUseCase.reconcileStuckExecutions(today);
        log.info("재확정 배치 종료 - date={}", today);

        log.info("예약이체 배치 시작 - date={}", today);
        scheduledTransferBatchUseCase.executeDaily(today);
        log.info("예약이체 배치 종료 - date={}", today);

        log.info("예약이체 재확정 배치 시작 - date={}", today);
        scheduledTransferBatchUseCase.reconcileStuckExecutions(today);
        log.info("예약이체 재확정 배치 종료 - date={}", today);
    }
}
