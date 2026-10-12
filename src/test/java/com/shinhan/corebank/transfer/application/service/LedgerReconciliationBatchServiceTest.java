package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import com.shinhan.corebank.transfer.application.port.in.LedgerFullReconciliationUseCase;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatch;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationUseCase;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;
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
    private static final int COB_HARD_THRESHOLD = 10;

    @Mock
    LedgerReconciliationUseCase ledgerReconciliationUseCase;

    @Mock
    LedgerFullReconciliationUseCase ledgerFullReconciliationUseCase;

    @Mock
    BatchExecutionLockPort batchExecutionLockPort;

    private LedgerReconciliationBatchService service() {
        // 대기 로직을 빠르게 돌리도록 poll 간격 1ms, 최대 대기 5ms(=5회 재확인)로 좁힌다.
        return new LedgerReconciliationBatchService(
                ledgerReconciliationUseCase,
                ledgerFullReconciliationUseCase,
                batchExecutionLockPort,
                Duration.ofMillis(1),
                Duration.ofMillis(5),
                COB_HARD_THRESHOLD);
    }

    private static List<LedgerReconciliationMismatch> mismatches(int count) {
        return LongStream.rangeClosed(1, count)
                .mapToObj(accountId -> new LedgerReconciliationMismatch(accountId, 1_000L, 900L))
                .toList();
    }

    @Test
    @DisplayName("COB 대사는 전수 대사를 실행하고, 단일 실행은 COB 락이 보장하므로 대사 락을 잡지 않는다")
    void runInCob_reconcilesAll_withoutReconciliationLock() {
        when(ledgerFullReconciliationUseCase.reconcileAll()).thenReturn(List.of());

        service().runInCob(DATE);

        verify(ledgerFullReconciliationUseCase).reconcileAll();
        verify(batchExecutionLockPort, never()).tryAcquire(anyString());
    }

    @Test
    @DisplayName("COB 대사 불일치가 임계 이하(soft)면 예외 없이 끝난다")
    void runInCob_mismatchesAtThreshold_soft_completesNormally() {
        when(ledgerFullReconciliationUseCase.reconcileAll()).thenReturn(mismatches(COB_HARD_THRESHOLD));

        assertThatCode(() -> service().runInCob(DATE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("COB 대사 불일치가 임계를 넘어도(hard) #556 전까지는 마감 중단 예외를 던지지 않는다(#600)")
    void runInCob_mismatchesOverThreshold_hard_doesNotHaltYet() {
        when(ledgerFullReconciliationUseCase.reconcileAll()).thenReturn(mismatches(COB_HARD_THRESHOLD + 1));

        assertThatCode(() -> service().runInCob(DATE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("전수 대사도 같은 락을 잡고 실행한 뒤 반납한다 — 증분 대사와 동시에 돌지 않는다(#468)")
    void runFull_acquiresSameLock_reconcilesAll_thenReleases() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);

        service().runFull();

        InOrder inOrder = inOrder(batchExecutionLockPort, ledgerFullReconciliationUseCase);
        inOrder.verify(batchExecutionLockPort).tryAcquire(JOB_NAME);
        inOrder.verify(ledgerFullReconciliationUseCase).reconcileAll();
        inOrder.verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("락 선점에 실패하면(증분 대사가 아직 실행 중) 전수 대사를 건너뛴다")
    void runFull_lockNotAcquired_skips() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(false);

        service().runFull();

        verify(ledgerFullReconciliationUseCase, never()).reconcileAll();
        verify(batchExecutionLockPort, never()).release(JOB_NAME);
    }

    @Test
    @DisplayName("전수 대사 중 예외가 나도 락은 반납된다")
    void runFull_fails_lockStillReleased() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        doThrow(new IllegalStateException("boom"))
                .when(ledgerFullReconciliationUseCase)
                .reconcileAll();

        service().runFull();

        verify(batchExecutionLockPort).release(JOB_NAME);
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
