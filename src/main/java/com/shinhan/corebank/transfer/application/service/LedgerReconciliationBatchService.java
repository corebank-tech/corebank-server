package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationBatchUseCase;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationUseCase;
import java.time.Duration;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LedgerReconciliationBatchService implements LedgerReconciliationBatchUseCase {

    private static final String JOB_NAME = "LEDGER_RECONCILIATION_BATCH";
    // batch.application.service.DailyTransferBatchService#JOB_NAME과 동일 문자열. 별도 공개
    // 상수가 없어 잡 이름 자체(batch_execution_lock.job_name 값)로 결합한다.
    private static final String DAILY_TRANSFER_BATCH_JOB_NAME = "DAILY_TRANSFER_BATCH";

    private final LedgerReconciliationUseCase ledgerReconciliationUseCase;
    private final BatchExecutionLockPort batchExecutionLockPort;
    private final Duration dailyBatchWaitPollInterval;
    private final Duration dailyBatchMaxWait;

    public LedgerReconciliationBatchService(
            LedgerReconciliationUseCase ledgerReconciliationUseCase,
            BatchExecutionLockPort batchExecutionLockPort,
            @Value("${app.ledger-reconciliation.daily-batch-wait-poll-interval:PT1M}")
                    Duration dailyBatchWaitPollInterval,
            @Value("${app.ledger-reconciliation.daily-batch-max-wait:PT1H}") Duration dailyBatchMaxWait) {
        this.ledgerReconciliationUseCase = ledgerReconciliationUseCase;
        this.batchExecutionLockPort = batchExecutionLockPort;
        this.dailyBatchWaitPollInterval = dailyBatchWaitPollInterval;
        this.dailyBatchMaxWait = dailyBatchMaxWait;
    }

    @Override
    public void run(LocalDate date) {
        if (!waitForDailyTransferBatchToFinish()) {
            log.error(
                    "[LEDGER_RECONCILIATION_SKIPPED] {} 배치가 {} 넘게 끝나지 않아 대사를 건너뜀 - date={}. " + "수동으로 이 날짜를 재확인해야 함",
                    DAILY_TRANSFER_BATCH_JOB_NAME,
                    dailyBatchMaxWait,
                    date);
            return;
        }
        if (!batchExecutionLockPort.tryAcquire(JOB_NAME)) {
            log.warn("이미 실행 중인 배치가 있어 이번 트리거는 건너뜀 - jobName={}", JOB_NAME);
            return;
        }
        try {
            log.info("원장-잔액 대사 배치 시작 - date={}", date);
            ledgerReconciliationUseCase.reconcile(date);
            log.info("원장-잔액 대사 배치 종료 - date={}", date);
        } catch (Exception e) {
            log.error("원장-잔액 대사 배치 실패 - date={}", date, e);
        } finally {
            batchExecutionLockPort.release(JOB_NAME);
        }
    }

    /**
     * DAILY_TRANSFER_BATCH(자동이체→예약이체)가 끝날 때까지 일정 간격으로 재확인하며 기다린다.
     * 고정 시각(예: 02시)에 "앞 배치가 끝나있겠지"라고 추측만 하지 않기 위함이다(#378, PR #463
     * 리뷰 - 배치 담당자 제안). 최대 대기시간을 넘겨도 안 끝나면 false를 반환해 이번 실행을
     * 건너뛰게 한다 — 조용히 다음 날짜로 넘어가지 않고 호출부가 ERROR 로그로 남겨야
     * 사람이 이 날짜를 수동으로 재확인할 수 있다.
     */
    private boolean waitForDailyTransferBatchToFinish() {
        long maxAttempts = Math.max(1, dailyBatchMaxWait.dividedBy(dailyBatchWaitPollInterval));
        for (long attempt = 1; attempt <= maxAttempts; attempt++) {
            if (!batchExecutionLockPort.isRunning(DAILY_TRANSFER_BATCH_JOB_NAME)) {
                return true;
            }
            log.info("{} 배치가 아직 실행 중 - {}번째 재확인 대기 (최대 {}번)", DAILY_TRANSFER_BATCH_JOB_NAME, attempt, maxAttempts);
            sleepQuietly(dailyBatchWaitPollInterval);
        }
        return false;
    }

    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
