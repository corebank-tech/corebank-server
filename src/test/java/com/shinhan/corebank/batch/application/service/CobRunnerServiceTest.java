package com.shinhan.corebank.batch.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.batch.api.BatchExecutionHistoryPort;
import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import com.shinhan.corebank.batch.api.BatchStepStatus;
import com.shinhan.corebank.batch.api.CobStep;
import com.shinhan.corebank.batch.api.CobStepHaltException;
import com.shinhan.corebank.business.api.BusinessDateAdvancer;
import com.shinhan.corebank.business.api.BusinessDateProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class CobRunnerServiceTest {

    private static final String JOB_NAME = "COB";
    private static final LocalDate COB_DATE = LocalDate.of(2026, 10, 2);
    private static final LocalDate NEXT_BUSINESS_DATE = LocalDate.of(2026, 10, 5);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-02T23:30:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    BatchExecutionLockPort batchExecutionLockPort;

    @Mock
    BusinessDateProvider businessDateProvider;

    @Mock
    BusinessDateAdvancer businessDateAdvancer;

    @Mock
    BatchExecutionHistoryPort batchExecutionHistoryPort;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private CobStep stepOf(String name, int order) {
        CobStep step = mock(CobStep.class);
        when(step.name()).thenReturn(name);
        when(step.order()).thenReturn(order);
        return step;
    }

    @Test
    @DisplayName("락 선점에 성공하면 영업일을 먼저 전환하고, 등록 순서와 무관하게 order() 오름차순으로 스텝을 실행한 뒤 락을 반납한다 (ADR-0004)")
    void run_acquiresLock_advancesFirst_runsStepsInOrder_thenReleases() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        when(businessDateAdvancer.advanceFrom(COB_DATE)).thenReturn(NEXT_BUSINESS_DATE);

        CobStep third = stepOf("third", 3);
        CobStep first = stepOf("first", 1);
        CobStep second = stepOf("second", 2);
        // 생성자에 등록 순서를 일부러 뒤섞어 넣는다 - order() 기준으로 재정렬되는지 확인
        CobRunnerService service = new CobRunnerService(
                batchExecutionLockPort,
                businessDateProvider,
                businessDateAdvancer,
                batchExecutionHistoryPort,
                CLOCK,
                meterRegistry,
                List.of(third, first, second));

        service.run(COB_DATE.plusDays(10));

        InOrder inOrder = inOrder(batchExecutionLockPort, businessDateAdvancer, first, second, third);
        inOrder.verify(batchExecutionLockPort).tryAcquire(JOB_NAME);
        inOrder.verify(businessDateAdvancer).advanceFrom(COB_DATE);
        inOrder.verify(first).run(COB_DATE);
        inOrder.verify(second).run(COB_DATE);
        inOrder.verify(third).run(COB_DATE);
        inOrder.verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("락 선점에 실패하면(이미 실행 중) 영업일 전환도 스텝도 실행하지 않고 release()도 부르지 않는다")
    void run_lockNotAcquired_skipsEverything() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(false);

        CobStep step = stepOf("step", 1);
        CobRunnerService service = new CobRunnerService(
                batchExecutionLockPort,
                businessDateProvider,
                businessDateAdvancer,
                batchExecutionHistoryPort,
                CLOCK,
                meterRegistry,
                List.of(step));

        service.run(COB_DATE);

        verify(businessDateAdvancer, never()).advanceFrom(any());
        verify(step, never()).run(any());
        verify(batchExecutionLockPort, never()).release(JOB_NAME);
    }

    @Test
    @DisplayName("영업일이 달력보다 미래면 전환도 스텝도 건너뛰지만, 락은 반납한다")
    void run_businessDateAfterCalendar_skipsAdvanceAndSteps_butReleasesLock() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(businessDateProvider.today()).thenReturn(COB_DATE);

        CobStep step = stepOf("step", 1);
        CobRunnerService service = new CobRunnerService(
                batchExecutionLockPort,
                businessDateProvider,
                businessDateAdvancer,
                batchExecutionHistoryPort,
                CLOCK,
                meterRegistry,
                List.of(step));

        service.run(COB_DATE.minusDays(1));

        verify(businessDateAdvancer, never()).advanceFrom(any());
        verify(step, never()).run(any());
        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    private static CannotAcquireLockException deadlock() {
        return new CannotAcquireLockException("deadlock", new SQLException("deadlock", "40001", 1213));
    }

    private CobRunnerService serviceWith(CobStep step) {
        return new CobRunnerService(
                batchExecutionLockPort,
                businessDateProvider,
                businessDateAdvancer,
                batchExecutionHistoryPort,
                CLOCK,
                meterRegistry,
                List.of(step));
    }

    @Test
    @DisplayName("영업일 전환이 락 충돌로 쫓겨나면 다시 시도하고, 성공하면 스텝을 이어서 실행한다")
    void run_advanceLosesLockConflict_retriesThenRunsSteps() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        when(businessDateAdvancer.advanceFrom(COB_DATE))
                .thenThrow(deadlock())
                .thenThrow(deadlock())
                .thenReturn(NEXT_BUSINESS_DATE);
        CobStep step = stepOf("step", 1);

        serviceWith(step).run(COB_DATE.plusDays(10));

        verify(businessDateAdvancer, times(3)).advanceFrom(COB_DATE);
        verify(step).run(COB_DATE);
        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("락 충돌이 계속되면 3번째에서 포기하고 스텝은 실행하지 않지만 락은 반납한다")
    void run_advanceKeepsLosingLockConflict_givesUpAfterThreeAttempts() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        when(businessDateAdvancer.advanceFrom(COB_DATE)).thenThrow(deadlock());
        CobStep step = stepOf("step", 1);
        CobRunnerService service = serviceWith(step);

        assertThatThrownBy(() -> service.run(COB_DATE.plusDays(10)))
                .isInstanceOf(PessimisticLockingFailureException.class);

        verify(businessDateAdvancer, times(3)).advanceFrom(COB_DATE);
        verify(step, never()).run(any());
        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("락 대기 시간 초과는 다시 시도하지 않고 바로 던진다")
    void run_advanceLockWaitTimeout_isNotRetried() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        when(businessDateAdvancer.advanceFrom(COB_DATE))
                .thenThrow(new CannotAcquireLockException(
                        "timeout", new SQLException("lock wait timeout", "HY000", 1205)));
        CobStep step = stepOf("step", 1);
        CobRunnerService service = serviceWith(step);

        assertThatThrownBy(() -> service.run(COB_DATE.plusDays(10)))
                .isInstanceOf(PessimisticLockingFailureException.class);

        verify(businessDateAdvancer, times(1)).advanceFrom(COB_DATE);
        verify(step, never()).run(any());
        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("락 충돌이 아닌 예외는 다시 시도하지 않고 바로 던진다")
    void run_advanceThrowsOtherException_isNotRetried() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        when(businessDateAdvancer.advanceFrom(COB_DATE)).thenThrow(new IllegalStateException("future date"));
        CobStep step = stepOf("step", 1);
        CobRunnerService service = serviceWith(step);

        assertThatThrownBy(() -> service.run(COB_DATE.plusDays(10))).isInstanceOf(IllegalStateException.class);

        verify(businessDateAdvancer, times(1)).advanceFrom(COB_DATE);
        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("order() 값이 중복된 스텝이 있으면 생성 시점에 바로 실패한다 (PR #543 리뷰 반영)")
    void constructor_duplicateOrder_throwsImmediately() {
        CobStep a = stepOf("a", 1);
        CobStep b = stepOf("b", 1);

        assertThatThrownBy(() -> new CobRunnerService(
                        batchExecutionLockPort,
                        businessDateProvider,
                        businessDateAdvancer,
                        batchExecutionHistoryPort,
                        CLOCK,
                        meterRegistry,
                        List.of(a, b)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("order 중복");
    }

    @Test
    @DisplayName("스텝 하나가 실패해도 나머지 스텝은 계속 실행되고 락은 반납된다 (도메인별 실패 격리)")
    void run_oneStepFails_remainingStepsStillRun_andLockReleased() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        when(businessDateAdvancer.advanceFrom(COB_DATE)).thenReturn(NEXT_BUSINESS_DATE);

        CobStep first = stepOf("first", 1);
        CobStep second = stepOf("second", 2);
        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
                .when(first)
                .run(COB_DATE);

        CobRunnerService service = new CobRunnerService(
                batchExecutionLockPort,
                businessDateProvider,
                businessDateAdvancer,
                batchExecutionHistoryPort,
                CLOCK,
                meterRegistry,
                List.of(first, second));

        service.run(COB_DATE.plusDays(10));

        verify(first).run(COB_DATE);
        verify(second).run(COB_DATE);
        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("스텝이 CobStepHaltException을 던지면 이후 스텝은 실행하지 않고 즉시 멈추지만, 락은 반납된다")
    void run_stepThrowsHaltException_skipsRemainingSteps_butLockReleased() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        when(businessDateAdvancer.advanceFrom(COB_DATE)).thenReturn(NEXT_BUSINESS_DATE);

        CobStep first = stepOf("first", 1);
        CobStep second = stepOf("second", 2);
        org.mockito.Mockito.doThrow(new CobStepHaltException("불일치 임계 초과"))
                .when(first)
                .run(COB_DATE);

        CobRunnerService service = new CobRunnerService(
                batchExecutionLockPort,
                businessDateProvider,
                businessDateAdvancer,
                batchExecutionHistoryPort,
                CLOCK,
                meterRegistry,
                List.of(first, second));

        service.run(COB_DATE.plusDays(10));

        verify(first).run(COB_DATE);
        verify(second, never()).run(any());
        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("스텝이 성공하면 SUCCESS 상태로, failureReason 없이 실행 기록을 남긴다 (#603)")
    void run_stepSucceeds_recordsSuccessHistory() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        when(businessDateAdvancer.advanceFrom(COB_DATE)).thenReturn(NEXT_BUSINESS_DATE);

        CobStep step = stepOf("step", 1);
        CobRunnerService service = new CobRunnerService(
                batchExecutionLockPort,
                businessDateProvider,
                businessDateAdvancer,
                batchExecutionHistoryPort,
                CLOCK,
                meterRegistry,
                List.of(step));

        service.run(COB_DATE.plusDays(10));

        verify(batchExecutionHistoryPort)
                .record(eq(JOB_NAME), eq("step"), eq(COB_DATE), any(), any(), eq(BatchStepStatus.SUCCESS), isNull());
    }

    @Test
    @DisplayName("스텝이 실패(soft)하면 FAILURE 상태와 예외 메시지를 실행 기록에 남긴다 (#603)")
    void run_stepFails_recordsFailureHistoryWithReason() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        when(businessDateAdvancer.advanceFrom(COB_DATE)).thenReturn(NEXT_BUSINESS_DATE);

        CobStep step = stepOf("step", 1);
        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
                .when(step)
                .run(COB_DATE);
        CobRunnerService service = new CobRunnerService(
                batchExecutionLockPort,
                businessDateProvider,
                businessDateAdvancer,
                batchExecutionHistoryPort,
                CLOCK,
                meterRegistry,
                List.of(step));

        service.run(COB_DATE.plusDays(10));

        verify(batchExecutionHistoryPort)
                .record(eq(JOB_NAME), eq("step"), eq(COB_DATE), any(), any(), eq(BatchStepStatus.FAILURE), eq("boom"));
    }

    @Test
    @DisplayName("스텝이 중단(hard)되면 HALTED 상태로 기록되고, 생략된 이후 스텝은 기록되지 않는다 (#603)")
    void run_stepHalts_recordsHaltedHistory_andSkipsRemainingRecords() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        when(businessDateAdvancer.advanceFrom(COB_DATE)).thenReturn(NEXT_BUSINESS_DATE);

        CobStep first = stepOf("first", 1);
        CobStep second = stepOf("second", 2);
        org.mockito.Mockito.doThrow(new CobStepHaltException("불일치 임계 초과"))
                .when(first)
                .run(COB_DATE);
        CobRunnerService service = new CobRunnerService(
                batchExecutionLockPort,
                businessDateProvider,
                businessDateAdvancer,
                batchExecutionHistoryPort,
                CLOCK,
                meterRegistry,
                List.of(first, second));

        service.run(COB_DATE.plusDays(10));

        verify(batchExecutionHistoryPort)
                .record(
                        eq(JOB_NAME),
                        eq("first"),
                        eq(COB_DATE),
                        any(),
                        any(),
                        eq(BatchStepStatus.HALTED),
                        eq("불일치 임계 초과"));
        verify(batchExecutionHistoryPort, never())
                .record(eq(JOB_NAME), eq("second"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("스텝이 성공하면 step·SUCCESS 태그로 카운터·소요시간이 1건씩 쌓인다 (#541)")
    void run_stepSucceeds_recordsStepMetrics() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        when(businessDateAdvancer.advanceFrom(COB_DATE)).thenReturn(NEXT_BUSINESS_DATE);

        CobStep step = stepOf("step", 1);
        CobRunnerService service = new CobRunnerService(
                batchExecutionLockPort,
                businessDateProvider,
                businessDateAdvancer,
                batchExecutionHistoryPort,
                CLOCK,
                meterRegistry,
                List.of(step));

        service.run(COB_DATE.plusDays(10));

        assertThat(meterRegistry
                        .get("cob.step.count")
                        .tags("step", "step", "status", "SUCCESS")
                        .counter()
                        .count())
                .isEqualTo(1.0);
        assertThat(meterRegistry
                        .get("cob.step.duration")
                        .tag("step", "step")
                        .timer()
                        .count())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("스텝이 실패(soft)하면 FAILURE 태그로 카운터가 쌓인다 (#541)")
    void run_stepFails_recordsFailureMetric() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        when(businessDateAdvancer.advanceFrom(COB_DATE)).thenReturn(NEXT_BUSINESS_DATE);

        CobStep step = stepOf("step", 1);
        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
                .when(step)
                .run(COB_DATE);
        CobRunnerService service = new CobRunnerService(
                batchExecutionLockPort,
                businessDateProvider,
                businessDateAdvancer,
                batchExecutionHistoryPort,
                CLOCK,
                meterRegistry,
                List.of(step));

        service.run(COB_DATE.plusDays(10));

        assertThat(meterRegistry
                        .get("cob.step.count")
                        .tags("step", "step", "status", "FAILURE")
                        .counter()
                        .count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("스텝이 중단(hard)되면 중단된 스텝만 HALTED 태그로 카운터가 쌓이고, 생략된 스텝은 0으로 남는다 (#541)")
    void run_stepHalts_recordsHaltedMetric_forHaltedStepOnly() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        when(businessDateAdvancer.advanceFrom(COB_DATE)).thenReturn(NEXT_BUSINESS_DATE);

        CobStep first = stepOf("first", 1);
        CobStep second = stepOf("second", 2);
        org.mockito.Mockito.doThrow(new CobStepHaltException("불일치 임계 초과"))
                .when(first)
                .run(COB_DATE);
        CobRunnerService service = new CobRunnerService(
                batchExecutionLockPort,
                businessDateProvider,
                businessDateAdvancer,
                batchExecutionHistoryPort,
                CLOCK,
                meterRegistry,
                List.of(first, second));

        service.run(COB_DATE.plusDays(10));

        assertThat(meterRegistry
                        .get("cob.step.count")
                        .tags("step", "first", "status", "HALTED")
                        .counter()
                        .count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.find("cob.step.count").tags("step", "second").counters().stream()
                        .mapToDouble(counter -> counter.count())
                        .sum())
                .isZero();
    }

    @Test
    @DisplayName("COB가 한 번도 안 돌아도 스텝 × 상태 3종의 카운터와 스텝별 타이머가 0으로 등록돼 있다 (#541)")
    void constructor_registersStepMetricsAtZero() {
        CobStep first = stepOf("first", 1);
        CobStep second = stepOf("second", 2);

        new CobRunnerService(
                batchExecutionLockPort,
                businessDateProvider,
                businessDateAdvancer,
                batchExecutionHistoryPort,
                CLOCK,
                meterRegistry,
                List.of(first, second));

        assertThat(meterRegistry.find("cob.step.count").counters()).hasSize(6);
        assertThat(meterRegistry.find("cob.step.duration").timers()).hasSize(2);
        assertThat(meterRegistry.find("cob.step.count").counters())
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
    }

    @Test
    @DisplayName("실행 기록 저장 자체가 실패해도 그 스텝은 성공 처리된 채로 나머지 스텝은 계속 실행된다 (#603, 셀프리뷰 반영)")
    void run_recordThrows_doesNotAbortRemainingSteps() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(businessDateProvider.today()).thenReturn(COB_DATE);
        when(businessDateAdvancer.advanceFrom(COB_DATE)).thenReturn(NEXT_BUSINESS_DATE);

        CobStep first = stepOf("first", 1);
        CobStep second = stepOf("second", 2);
        org.mockito.Mockito.doThrow(new RuntimeException("DB 순단"))
                .when(batchExecutionHistoryPort)
                .record(eq(JOB_NAME), eq("first"), any(), any(), any(), any(), any());
        CobRunnerService service = new CobRunnerService(
                batchExecutionLockPort,
                businessDateProvider,
                businessDateAdvancer,
                batchExecutionHistoryPort,
                CLOCK,
                meterRegistry,
                List.of(first, second));

        service.run(COB_DATE.plusDays(10));

        verify(second).run(COB_DATE);
        verify(batchExecutionLockPort).release(JOB_NAME);
    }
}
