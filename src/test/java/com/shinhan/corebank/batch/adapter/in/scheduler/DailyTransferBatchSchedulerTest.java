package com.shinhan.corebank.batch.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyTransferBatchSchedulerTest {

    private static final String JOB_NAME = "DAILY_TRANSFER_BATCH";

    @Mock
    AutoTransferBatchUseCase autoTransferBatchUseCase;

    @Mock
    ScheduledTransferBatchUseCase scheduledTransferBatchUseCase;

    @Mock
    BatchExecutionLockPort batchExecutionLockPort;

    private DailyTransferBatchScheduler scheduler(Clock clock) {
        return new DailyTransferBatchScheduler(
                autoTransferBatchUseCase, scheduledTransferBatchUseCase, batchExecutionLockPort, clock);
    }

    private Clock fixedClock(LocalDate date) {
        return Clock.fixed(date.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("락 선점에 성공하면 자동이체 실행→재확정 다음에 예약이체 실행→재확정 순으로 4단계를 호출하고, 끝나면 락을 반납한다 (POL-037)")
    void runDailyBatch_acquiresLock_runsInOrder_thenReleases() {
        LocalDate fixedDate = LocalDate.of(2026, 3, 15);
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);

        scheduler(fixedClock(fixedDate)).runDailyBatch();

        InOrder inOrder = inOrder(batchExecutionLockPort, autoTransferBatchUseCase, scheduledTransferBatchUseCase);
        inOrder.verify(batchExecutionLockPort).tryAcquire(JOB_NAME);
        inOrder.verify(autoTransferBatchUseCase).executeDaily(fixedDate);
        inOrder.verify(autoTransferBatchUseCase).reconcileStuckExecutions(fixedDate);
        inOrder.verify(scheduledTransferBatchUseCase).executeDaily(fixedDate);
        inOrder.verify(scheduledTransferBatchUseCase).reconcileStuckExecutions(fixedDate);
        inOrder.verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("락 선점에 실패하면(이미 실행 중) 4단계 어느 것도 호출하지 않고 release()도 부르지 않는다")
    void runDailyBatch_lockNotAcquired_skipsEverything() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(false);

        scheduler(fixedClock(LocalDate.of(2026, 3, 15))).runDailyBatch();

        verify(autoTransferBatchUseCase, never()).executeDaily(any());
        verify(autoTransferBatchUseCase, never()).reconcileStuckExecutions(any());
        verify(scheduledTransferBatchUseCase, never()).executeDaily(any());
        verify(scheduledTransferBatchUseCase, never()).reconcileStuckExecutions(any());
        verify(batchExecutionLockPort, never()).release(anyString());
    }

    @Test
    @DisplayName("배치 도중 예외가 나도 락은 항상 반납된다 (try/finally)")
    void runDailyBatch_exceptionDuringBatch_stillReleasesLock() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        doThrow(new IllegalStateException("boom")).when(autoTransferBatchUseCase).reconcileStuckExecutions(any());

        assertThatThrownBy(() -> scheduler(fixedClock(LocalDate.of(2026, 3, 15))).runDailyBatch())
                .isInstanceOf(IllegalStateException.class);

        verify(batchExecutionLockPort).release(JOB_NAME);
        verify(scheduledTransferBatchUseCase, never()).executeDaily(any());
    }
}
