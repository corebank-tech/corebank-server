package com.shinhan.corebank.batch.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferBatchUseCase;
import com.shinhan.corebank.batch.application.port.out.BatchExecutionLockPort;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferBatchUseCase;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyTransferBatchServiceTest {

    private static final String JOB_NAME = "DAILY_TRANSFER_BATCH";
    private static final LocalDate DATE = LocalDate.of(2026, 3, 15);

    @Mock
    AutoTransferBatchUseCase autoTransferBatchUseCase;

    @Mock
    ScheduledTransferBatchUseCase scheduledTransferBatchUseCase;

    @Mock
    BatchExecutionLockPort batchExecutionLockPort;

    private DailyTransferBatchService service() {
        return new DailyTransferBatchService(
                autoTransferBatchUseCase, scheduledTransferBatchUseCase, batchExecutionLockPort);
    }

    @Test
    @DisplayName("락 선점에 성공하면 자동이체 실행→재확정 다음에 예약이체 실행→재확정 순으로 4단계를 호출하고, 끝나면 락을 반납한다 (POL-037)")
    void run_acquiresLock_runsInOrder_thenReleases() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);

        service().run(DATE);

        InOrder inOrder = inOrder(batchExecutionLockPort, autoTransferBatchUseCase, scheduledTransferBatchUseCase);
        inOrder.verify(batchExecutionLockPort).tryAcquire(JOB_NAME);
        inOrder.verify(autoTransferBatchUseCase).executeDaily(DATE);
        inOrder.verify(autoTransferBatchUseCase).reconcileStuckExecutions(DATE);
        inOrder.verify(scheduledTransferBatchUseCase).executeDaily(DATE);
        inOrder.verify(scheduledTransferBatchUseCase).reconcileStuckExecutions(DATE);
        inOrder.verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("락 선점에 실패하면(이미 실행 중) 4단계 어느 것도 호출하지 않고 release()도 부르지 않는다")
    void run_lockNotAcquired_skipsEverything() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(false);

        service().run(DATE);

        verify(autoTransferBatchUseCase, never()).executeDaily(any());
        verify(autoTransferBatchUseCase, never()).reconcileStuckExecutions(any());
        verify(scheduledTransferBatchUseCase, never()).executeDaily(any());
        verify(scheduledTransferBatchUseCase, never()).reconcileStuckExecutions(any());
        verify(batchExecutionLockPort, never()).release(anyString());
    }

    @Test
    @DisplayName("자동이체 단계에서 예외가 나도 예약이체 단계는 계속 진행되고 락은 반납된다 (도메인별 실패 격리, PR #272 리뷰)")
    void run_autoTransferStepFails_scheduledTransferStepStillRuns_andLockReleased() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        doThrow(new IllegalStateException("boom"))
                .when(autoTransferBatchUseCase)
                .executeDaily(DATE);

        service().run(DATE);

        verify(autoTransferBatchUseCase).executeDaily(DATE);
        verify(autoTransferBatchUseCase, never()).reconcileStuckExecutions(any());
        verify(scheduledTransferBatchUseCase).executeDaily(DATE);
        verify(scheduledTransferBatchUseCase).reconcileStuckExecutions(DATE);
        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("예약이체 단계에서 예외가 나도 락은 반납된다")
    void run_scheduledTransferStepFails_lockStillReleased() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        doThrow(new IllegalStateException("boom"))
                .when(scheduledTransferBatchUseCase)
                .executeDaily(DATE);

        service().run(DATE);

        verify(autoTransferBatchUseCase).executeDaily(DATE);
        verify(autoTransferBatchUseCase).reconcileStuckExecutions(DATE);
        verify(scheduledTransferBatchUseCase).executeDaily(DATE);
        verify(scheduledTransferBatchUseCase, never()).reconcileStuckExecutions(any());
        verify(batchExecutionLockPort).release(JOB_NAME);
    }
}
