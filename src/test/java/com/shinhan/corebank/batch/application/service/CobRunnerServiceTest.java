package com.shinhan.corebank.batch.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import com.shinhan.corebank.batch.api.CobStep;
import com.shinhan.corebank.business.api.BusinessDateAdvancer;
import com.shinhan.corebank.business.api.BusinessDateProvider;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CobRunnerServiceTest {

    private static final String JOB_NAME = "COB";
    private static final LocalDate COB_DATE = LocalDate.of(2026, 10, 2);
    private static final LocalDate NEXT_BUSINESS_DATE = LocalDate.of(2026, 10, 5);

    @Mock
    BatchExecutionLockPort batchExecutionLockPort;

    @Mock
    BusinessDateProvider businessDateProvider;

    @Mock
    BusinessDateAdvancer businessDateAdvancer;

    private CobStep stepOf(String name, int order) {
        CobStep step = org.mockito.Mockito.mock(CobStep.class);
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
                batchExecutionLockPort, businessDateProvider, businessDateAdvancer, List.of(third, first, second));

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
        CobRunnerService service =
                new CobRunnerService(batchExecutionLockPort, businessDateProvider, businessDateAdvancer, List.of(step));

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
        CobRunnerService service =
                new CobRunnerService(batchExecutionLockPort, businessDateProvider, businessDateAdvancer, List.of(step));

        service.run(COB_DATE.minusDays(1));

        verify(businessDateAdvancer, never()).advanceFrom(any());
        verify(step, never()).run(any());
        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("order() 값이 중복된 스텝이 있으면 생성 시점에 바로 실패한다 (PR #543 리뷰 반영)")
    void constructor_duplicateOrder_throwsImmediately() {
        CobStep a = stepOf("a", 1);
        CobStep b = stepOf("b", 1);

        assertThatThrownBy(() -> new CobRunnerService(
                        batchExecutionLockPort, businessDateProvider, businessDateAdvancer, List.of(a, b)))
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
                batchExecutionLockPort, businessDateProvider, businessDateAdvancer, List.of(first, second));

        service.run(COB_DATE.plusDays(10));

        verify(first).run(COB_DATE);
        verify(second).run(COB_DATE);
        verify(batchExecutionLockPort).release(JOB_NAME);
    }
}
