package com.shinhan.corebank.autotransfer.adapter.in.scheduler;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferBatchUseCase;
import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// #365 - PROCESSING에 멈춘 회차가 메인 배치(00:10) 한 번만 기다리면 최대 24시간 노출된다.
// 별도 주기로 돌려 그 시간을 줄인다.
@Component
@RequiredArgsConstructor
public class AutoTransferReconcileScheduler {
    private static final Logger log = LoggerFactory.getLogger(AutoTransferReconcileScheduler.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String JOB_NAME = "AUTO_TRANSFER_RECONCILE";
    // DailyTransferBatchService.JOB_NAME과 같은 값 - 메인 배치가 처리 중인 회차를
    // "멈춘 것"으로 오판하지 않으려고 실행 중인지만 확인한다(락은 안 잡음).
    private static final String DAILY_TRANSFER_BATCH_JOB_NAME = "DAILY_TRANSFER_BATCH";

    private final AutoTransferBatchUseCase autoTransferBatchUseCase;
    private final BatchExecutionLockPort batchExecutionLockPort;
    private final Clock clock;

    // 메인 배치(00:10)와 겹치지 않는 시각에 2시간마다. 토큰정리(:15)·멱등키정리(:00)와도 안 겹침.
    @Scheduled(cron = "0 30 */2 * * *", zone = "Asia/Seoul")
    public void reconcile() {
        if (batchExecutionLockPort.isRunning(DAILY_TRANSFER_BATCH_JOB_NAME)) {
            log.info("메인 일일배치 실행 중이라 이번 재확정 주기는 건너뜀 - jobName={}", DAILY_TRANSFER_BATCH_JOB_NAME);
            return;
        }
        if (!batchExecutionLockPort.tryAcquire(JOB_NAME)) {
            log.warn("이미 실행 중인 재확정 배치가 있어 이번 트리거는 건너뜀 - jobName={}", JOB_NAME);
            return;
        }
        try {
            LocalDate date = LocalDate.now(clock.withZone(SEOUL));
            log.info("독립 재확정 배치 시작 - date={}", date);
            autoTransferBatchUseCase.reconcileStuckExecutions(date);
            log.info("독립 재확정 배치 종료 - date={}", date);
        } finally {
            batchExecutionLockPort.release(JOB_NAME);
        }
    }
}
