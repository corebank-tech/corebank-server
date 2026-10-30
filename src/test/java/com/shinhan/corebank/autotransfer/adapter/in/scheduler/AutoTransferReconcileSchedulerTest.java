package com.shinhan.corebank.autotransfer.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferBatchUseCase;
import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutoTransferReconcileSchedulerTest {

    private static final String JOB_NAME = "AUTO_TRANSFER_RECONCILE";
    private static final String DAILY_TRANSFER_BATCH_JOB_NAME = "DAILY_TRANSFER_BATCH";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T00:30:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    AutoTransferBatchUseCase autoTransferBatchUseCase;

    @Mock
    BatchExecutionLockPort batchExecutionLockPort;

    @Test
    @DisplayName("메인 일일배치가 실행 중이면 재확정을 건너뛰고, 자기 락도 건드리지 않는다 (#365)")
    void reconcile_mainBatchRunning_skips() {
        when(batchExecutionLockPort.isRunning(DAILY_TRANSFER_BATCH_JOB_NAME)).thenReturn(true);

        new AutoTransferReconcileScheduler(autoTransferBatchUseCase, batchExecutionLockPort, CLOCK).reconcile();

        verify(batchExecutionLockPort, never()).tryAcquire(any());
        verify(autoTransferBatchUseCase, never()).reconcileStuckExecutions(any());
    }

    @Test
    @DisplayName("메인 배치가 안 돌고 자기 락도 비어 있으면 재확정을 실행하고 락을 반납한다 (#365)")
    void reconcile_mainBatchIdle_runsAndReleasesLock() {
        when(batchExecutionLockPort.isRunning(DAILY_TRANSFER_BATCH_JOB_NAME)).thenReturn(false);
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);

        new AutoTransferReconcileScheduler(autoTransferBatchUseCase, batchExecutionLockPort, CLOCK).reconcile();

        verify(autoTransferBatchUseCase).reconcileStuckExecutions(TODAY);
        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("이전 주기의 재확정이 아직 돌고 있으면(자기 락 획득 실패) 건너뛴다 (#365)")
    void reconcile_selfLockAlreadyHeld_skips() {
        when(batchExecutionLockPort.isRunning(DAILY_TRANSFER_BATCH_JOB_NAME)).thenReturn(false);
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(false);

        new AutoTransferReconcileScheduler(autoTransferBatchUseCase, batchExecutionLockPort, CLOCK).reconcile();

        verify(autoTransferBatchUseCase, never()).reconcileStuckExecutions(any());
        verify(batchExecutionLockPort, never()).release(any());
    }

    @Test
    @DisplayName("재확정 처리 중 예외가 나도 락은 반납된다 (#365)")
    void reconcile_useCaseThrows_stillReleasesLock() {
        when(batchExecutionLockPort.isRunning(DAILY_TRANSFER_BATCH_JOB_NAME)).thenReturn(false);
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        doThrow(new RuntimeException("boom")).when(autoTransferBatchUseCase).reconcileStuckExecutions(any());

        assertThatThrownBy(() -> new AutoTransferReconcileScheduler(
                                autoTransferBatchUseCase, batchExecutionLockPort, CLOCK)
                        .reconcile())
                .isInstanceOf(RuntimeException.class);

        verify(batchExecutionLockPort).release(JOB_NAME);
    }
}
