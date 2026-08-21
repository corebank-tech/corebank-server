package com.shinhan.corebank.autotransfer.adapter.in.scheduler;

import static org.mockito.Mockito.inOrder;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferBatchUseCase;
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
class AutoTransferBatchSchedulerTest {

    @Mock
    AutoTransferBatchUseCase autoTransferBatchUseCase;

    @Mock
    ScheduledTransferBatchUseCase scheduledTransferBatchUseCase;

    @Test
    @DisplayName("자동이체 실행→재확정 다음에 예약이체 실행→재확정 순으로, 같은 트리거 안에서 4단계를 순서대로 호출한다 (POL-037)")
    void runDailyBatch_callsAutoTransferThenScheduledTransferInOrder() {
        LocalDate fixedDate = LocalDate.of(2026, 3, 15);
        Clock fixedClock = Clock.fixed(
                fixedDate.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul"));
        AutoTransferBatchScheduler scheduler =
                new AutoTransferBatchScheduler(autoTransferBatchUseCase, scheduledTransferBatchUseCase, fixedClock);

        scheduler.runDailyBatch();

        InOrder inOrder = inOrder(autoTransferBatchUseCase, scheduledTransferBatchUseCase);
        inOrder.verify(autoTransferBatchUseCase).executeDaily(fixedDate);
        inOrder.verify(autoTransferBatchUseCase).reconcileStuckExecutions(fixedDate);
        inOrder.verify(scheduledTransferBatchUseCase).executeDaily(fixedDate);
        inOrder.verify(scheduledTransferBatchUseCase).reconcileStuckExecutions(fixedDate);
    }
}
