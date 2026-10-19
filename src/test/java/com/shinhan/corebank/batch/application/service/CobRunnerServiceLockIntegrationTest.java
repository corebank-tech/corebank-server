package com.shinhan.corebank.batch.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.batch.api.BatchExecutionHistoryPort;
import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import com.shinhan.corebank.batch.api.CobStep;
import com.shinhan.corebank.business.api.BusinessDateAdvancer;
import com.shinhan.corebank.business.api.BusinessDateProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 락만 실제 행으로 검증한다(#637) — 실제 영업일·스텝은 공유 테스트 DB의 영업일을 넘기므로 대역으로 둔다.
class CobRunnerServiceLockIntegrationTest extends IntegrationTestSupport {

    private static final String JOB_NAME = "COB";
    private static final LocalDate COB_DATE = LocalDate.of(2026, 10, 19);

    @Autowired
    BatchExecutionLockPort batchExecutionLockPort;

    @Autowired
    Clock clock;

    @Test
    @DisplayName("Flyway가 넣은 COB 락 행으로 락을 잡은 채 스텝을 실행하고, 끝나면 락을 반납한다")
    void run_withMigratedCobLockRow_holdsLockDuringStepsAndReleases() {
        BusinessDateProvider businessDateProvider = mock(BusinessDateProvider.class);
        BusinessDateAdvancer businessDateAdvancer = mock(BusinessDateAdvancer.class);
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        when(businessDateAdvancer.advanceFrom(COB_DATE)).thenReturn(COB_DATE.plusDays(1));

        AtomicBoolean lockHeldDuringStep = new AtomicBoolean(false);
        CobStep step = mock(CobStep.class);
        when(step.name()).thenReturn("lock-check");
        when(step.order()).thenReturn(1);
        doAnswer(invocation -> {
                    lockHeldDuringStep.set(batchExecutionLockPort.isRunning(JOB_NAME));
                    return null;
                })
                .when(step)
                .run(any());

        CobRunnerService runner = new CobRunnerService(
                batchExecutionLockPort,
                businessDateProvider,
                businessDateAdvancer,
                mock(BatchExecutionHistoryPort.class),
                clock,
                new SimpleMeterRegistry(),
                List.of(step));

        runner.run(COB_DATE);

        verify(step).run(COB_DATE);
        assertThat(lockHeldDuringStep).isTrue();
        assertThat(batchExecutionLockPort.isRunning(JOB_NAME)).isFalse();
    }
}
