package com.shinhan.corebank.batch.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.batch.api.BatchExecutionHistoryPort;
import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import com.shinhan.corebank.batch.api.CobStep;
import com.shinhan.corebank.batch.application.port.out.CobCheckpointPort;
import com.shinhan.corebank.business.api.BusinessDateAdvancer;
import com.shinhan.corebank.business.api.BusinessDateProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 체크포인트만 실제 DB로 검증한다 — 실제 영업일은 공유 테스트 DB를 넘기므로 대역으로 두고, 커밋이 검증 대상이라 @Transactional 을 걸지 않는다(#663)
class CobRunnerServiceRestartIntegrationTest extends IntegrationTestSupport {

    // 시드·다른 테스트와 겹치지 않는 먼 미래 날짜와 이 테스트 전용 스텝 이름
    private static final LocalDate COB_DATE = LocalDate.of(2663, 1, 3);
    private static final LocalDate NEXT_DATE = LocalDate.of(2663, 1, 4);
    private static final String STEP_A = "restart-663-a";
    private static final String STEP_B = "restart-663-b";

    @Autowired
    CobCheckpointPort cobCheckpointPort;

    @Autowired
    Clock clock;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM cob_checkpoint WHERE step_name IN (?, ?, 'business-date-advance') AND cob_date IN (?, ?)",
                STEP_A,
                STEP_B,
                COB_DATE,
                NEXT_DATE);
    }

    private String statusOf(LocalDate cobDate, String stepName) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM cob_checkpoint WHERE cob_date = ? AND step_name = ?",
                String.class,
                cobDate,
                stepName);
    }

    private CobStep step(String name, int order) {
        CobStep step = mock(CobStep.class);
        when(step.name()).thenReturn(name);
        when(step.order()).thenReturn(order);
        return step;
    }

    @Test
    @DisplayName("스텝이 실패한 뒤 다시 실행하면 영업일을 한 번 더 넘기지 않고, 끝난 스텝은 건너뛰고 실패한 스텝만 같은 날짜로 다시 한다")
    void run_afterStepFailure_resumesFailedStepOnly_withoutAdvancingAgain() {
        BusinessDateProvider businessDateProvider = mock(BusinessDateProvider.class);
        BusinessDateAdvancer businessDateAdvancer = mock(BusinessDateAdvancer.class);
        BatchExecutionLockPort lockPort = mock(BatchExecutionLockPort.class);
        when(lockPort.tryAcquire("COB")).thenReturn(true);
        when(businessDateAdvancer.advanceFrom(COB_DATE)).thenReturn(NEXT_DATE);
        when(businessDateAdvancer.advanceFrom(NEXT_DATE)).thenReturn(NEXT_DATE.plusDays(1));
        AtomicBoolean stepBFails = new AtomicBoolean(true);
        CobStep stepA = step(STEP_A, 1);
        CobStep stepB = step(STEP_B, 2);
        CobRunnerService runner = new CobRunnerService(
                lockPort,
                businessDateProvider,
                businessDateAdvancer,
                mock(BatchExecutionHistoryPort.class),
                cobCheckpointPort,
                clock,
                new SimpleMeterRegistry(),
                List.of(stepA, stepB));

        // 첫 밤: 영업일이 COB_DATE이고 B가 실패한다
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        doAnswer(invocation -> {
                    if (stepBFails.get()) {
                        throw new RuntimeException("B 실패");
                    }
                    return null;
                })
                .when(stepB)
                .run(any());
        runner.run(NEXT_DATE);

        assertThat(statusOf(COB_DATE, "business-date-advance")).isEqualTo("SUCCESS");
        assertThat(statusOf(COB_DATE, STEP_A)).isEqualTo("SUCCESS");
        assertThat(statusOf(COB_DATE, STEP_B)).isEqualTo("PENDING");

        // 다음 밤: 영업일은 이미 NEXT_DATE로 넘어가 있고 B가 복구돼 있다
        stepBFails.set(false);
        when(businessDateProvider.today()).thenReturn(NEXT_DATE);
        runner.run(NEXT_DATE);

        verify(businessDateAdvancer, times(1)).advanceFrom(COB_DATE);
        verify(stepA, times(1)).run(COB_DATE);
        verify(stepB, times(2)).run(COB_DATE);
        verify(stepA, times(1)).run(NEXT_DATE);
        verify(stepB, times(1)).run(NEXT_DATE);
        assertThat(statusOf(COB_DATE, STEP_B)).isEqualTo("SUCCESS");
        assertThat(statusOf(NEXT_DATE, STEP_B)).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("미완료가 없으면 재개 없이 새 COB만 시작한다")
    void run_withoutIncomplete_startsNewCobOnly() {
        BusinessDateProvider businessDateProvider = mock(BusinessDateProvider.class);
        BusinessDateAdvancer businessDateAdvancer = mock(BusinessDateAdvancer.class);
        BatchExecutionLockPort lockPort = mock(BatchExecutionLockPort.class);
        when(lockPort.tryAcquire("COB")).thenReturn(true);
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        when(businessDateAdvancer.advanceFrom(COB_DATE)).thenReturn(NEXT_DATE);
        CobStep stepA = step(STEP_A, 1);
        CobRunnerService runner = new CobRunnerService(
                lockPort,
                businessDateProvider,
                businessDateAdvancer,
                mock(BatchExecutionHistoryPort.class),
                cobCheckpointPort,
                clock,
                new SimpleMeterRegistry(),
                List.of(stepA));

        runner.run(NEXT_DATE);

        verify(stepA, times(1)).run(COB_DATE);
        verify(businessDateAdvancer, never()).advanceFrom(NEXT_DATE);
        assertThat(statusOf(COB_DATE, STEP_A)).isEqualTo("SUCCESS");
    }
}
