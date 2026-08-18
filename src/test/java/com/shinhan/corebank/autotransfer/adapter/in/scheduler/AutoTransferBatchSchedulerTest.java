package com.shinhan.corebank.autotransfer.adapter.in.scheduler;

import static org.mockito.Mockito.inOrder;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferBatchUseCase;
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
class AutoTransferBatchSchedulerTest {

    @Mock
    AutoTransferBatchUseCase autoTransferBatchUseCase;

    @Test
    @DisplayName("executeDaily(date) 직후 같은 트리거 안에서 reconcileStuckExecutions(date)를 이어서 호출한다")
    void runDailyBatch_callsExecuteDailyThenReconcile() {
        LocalDate fixedDate = LocalDate.of(2026, 3, 15);
        Clock fixedClock = Clock.fixed(
                fixedDate.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul"));
        AutoTransferBatchScheduler scheduler =
                new AutoTransferBatchScheduler(autoTransferBatchUseCase, fixedClock);

        scheduler.runDailyBatch();

        InOrder inOrder = inOrder(autoTransferBatchUseCase);
        inOrder.verify(autoTransferBatchUseCase).executeDaily(fixedDate);
        inOrder.verify(autoTransferBatchUseCase).reconcileStuckExecutions(fixedDate);
    }
}
