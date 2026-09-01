package com.shinhan.corebank.batch.application.service;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferBatchUseCase;
import com.shinhan.corebank.batch.application.port.in.DailyTransferBatchUseCase;
import com.shinhan.corebank.batch.application.port.out.BatchExecutionLockPort;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferBatchUseCase;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DailyTransferBatchService implements DailyTransferBatchUseCase {
    private static final Logger log = LoggerFactory.getLogger(DailyTransferBatchService.class);
    private static final String JOB_NAME = "DAILY_TRANSFER_BATCH";

    private final AutoTransferBatchUseCase autoTransferBatchUseCase;
    private final ScheduledTransferBatchUseCase scheduledTransferBatchUseCase;
    private final BatchExecutionLockPort batchExecutionLockPort;

    @Override
    public void run(LocalDate date) {
        if (!batchExecutionLockPort.tryAcquire(JOB_NAME)) {
            log.warn("이미 실행 중인 배치가 있어 이번 트리거는 건너뜀 - jobName={}", JOB_NAME);
            return;
        }
        try {
            runAutoTransferSteps(date);
            runScheduledTransferSteps(date);
        } finally {
            batchExecutionLockPort.release(JOB_NAME);
        }
    }

    private void runAutoTransferSteps(LocalDate date) {
        try {
            log.info("자동이체 배치 시작 - date={}", date);
            autoTransferBatchUseCase.executeDaily(date);
            log.info("자동이체 배치 종료 - date={}", date);

            log.info("재확정 배치 시작 - date={}", date);
            autoTransferBatchUseCase.reconcileStuckExecutions(date);
            log.info("재확정 배치 종료 - date={}", date);
        } catch (Exception e) {
            log.error("자동이체 배치 단계 실패 - 예약이체 배치는 계속 진행함 - date={}", date, e);
        }
    }

    private void runScheduledTransferSteps(LocalDate date) {
        try {
            log.info("예약이체 배치 시작 - date={}", date);
            scheduledTransferBatchUseCase.executeDaily(date);
            log.info("예약이체 배치 종료 - date={}", date);

            log.info("예약이체 재확정 배치 시작 - date={}", date);
            scheduledTransferBatchUseCase.reconcileStuckExecutions(date);
            log.info("예약이체 재확정 배치 종료 - date={}", date);
        } catch (Exception e) {
            log.error("예약이체 배치 단계 실패 - date={}", date, e);
        }
    }
}
