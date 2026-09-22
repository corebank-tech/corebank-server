package com.shinhan.corebank.transfer.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationUseCase;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerReconciliationBatchServiceTest {

    private static final String JOB_NAME = "LEDGER_RECONCILIATION_BATCH";
    private static final String DAILY_BATCH_JOB_NAME = "DAILY_TRANSFER_BATCH";
    private static final LocalDate DATE = LocalDate.of(2026, 8, 9);

    @Mock
    LedgerReconciliationUseCase ledgerReconciliationUseCase;

    @Mock
    BatchExecutionLockPort batchExecutionLockPort;

    private LedgerReconciliationBatchService service() {
        // 대기 로직을 빠르게 돌리도록 poll 간격 1ms, 최대 대기 5ms(=5회 재확인)로 좁힌다.
        return new LedgerReconciliationBatchService(
                ledgerReconciliationUseCase, batchExecutionLockPort, Duration.ofMillis(1), Duration.ofMillis(5));
    }

    @Test
    @DisplayName("락 선점에 성공하면 대사를 실행하고 끝나면 락을 반납한다")
    void run_acquiresLock_reconciles_thenReleases() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);

        service().run(DATE);

        InOrder inOrder = inOrder(batchExecutionLockPort, ledgerReconciliationUseCase);
        inOrder.verify(batchExecutionLockPort).tryAcquire(JOB_NAME);
        inOrder.verify(ledgerReconciliationUseCase).reconcile(DATE);
        inOrder.verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("락 선점에 실패하면(이미 실행 중) 대사를 실행하지 않고 release()도 부르지 않는다")
    void run_lockNotAcquired_skipsReconcile() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(false);

        service().run(DATE);

        verify(ledgerReconciliationUseCase, never()).reconcile(any());
        verify(batchExecutionLockPort, never()).release(JOB_NAME);
    }

    @Test
    @DisplayName("대사 중 예외가 나도 락은 반납된다")
    void run_reconcileFails_lockStillReleased() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        doThrow(new IllegalStateException("boom"))
                .when(ledgerReconciliationUseCase)
                .reconcile(DATE);

        service().run(DATE);

        verify(ledgerReconciliationUseCase).reconcile(DATE);
        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("자동이체·예약이체 배치가 이미 끝나 있으면 곧바로 대사 락을 잡고 실행한다")
    void run_dailyBatchAlreadyFinished_proceedsImmediately() {
        when(batchExecutionLockPort.isRunning(DAILY_BATCH_JOB_NAME)).thenReturn(false);
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);

        service().run(DATE);

        verify(batchExecutionLockPort, times(1)).isRunning(DAILY_BATCH_JOB_NAME);
        verify(ledgerReconciliationUseCase).reconcile(DATE);
    }

    @Test
    @DisplayName("자동이체·예약이체 배치가 돌다가 대기 중 끝나면 재확인 후 대사를 실행한다")
    void run_dailyBatchFinishesDuringWait_proceedsAfterPolling() {
        when(batchExecutionLockPort.isRunning(DAILY_BATCH_JOB_NAME)).thenReturn(true, true, false);
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);

        service().run(DATE);

        verify(batchExecutionLockPort, times(3)).isRunning(DAILY_BATCH_JOB_NAME);
        verify(ledgerReconciliationUseCase).reconcile(DATE);
    }

    @Test
    @DisplayName("자동이체·예약이체 배치가 최대 대기시간 내내 안 끝나면 대사를 건너뛰고 락도 잡지 않는다")
    void run_dailyBatchNeverFinishes_skipsReconcileWithoutAcquiringLock() {
        when(batchExecutionLockPort.isRunning(DAILY_BATCH_JOB_NAME)).thenReturn(true);

        service().run(DATE);

        verify(batchExecutionLockPort, never()).tryAcquire(JOB_NAME);
        verify(ledgerReconciliationUseCase, never()).reconcile(any());
        verify(batchExecutionLockPort, never()).release(JOB_NAME);
    }
}
