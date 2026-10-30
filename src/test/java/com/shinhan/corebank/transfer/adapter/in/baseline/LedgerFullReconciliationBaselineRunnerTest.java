package com.shinhan.corebank.transfer.adapter.in.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import com.shinhan.corebank.transfer.application.port.in.LedgerFullReconciliationUseCase;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatch;
import com.shinhan.corebank.transfer.application.service.LedgerReconciliationBatchService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PH-38 전수 대사 베이스라인 실행기")
class LedgerFullReconciliationBaselineRunnerTest {

    private static final String JOB_NAME = LedgerReconciliationBatchService.JOB_NAME;

    @Mock
    private LedgerFullReconciliationUseCase ledgerFullReconciliationUseCase;

    @Mock
    private BatchExecutionLockPort batchExecutionLockPort;

    @Test
    @DisplayName("대사 락을 잡고 설정한 횟수만큼 전수 대사를 돌린 뒤 락을 푼다")
    void runsReconciliationForEachRoundInsideLock() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(ledgerFullReconciliationUseCase.reconcileAll()).thenReturn(List.of());

        runner(3, 1).measure();

        InOrder inOrder = inOrder(batchExecutionLockPort, ledgerFullReconciliationUseCase);
        inOrder.verify(batchExecutionLockPort).tryAcquire(JOB_NAME);
        inOrder.verify(ledgerFullReconciliationUseCase, times(3)).reconcileAll();
        inOrder.verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("예열 회차는 통계에서 빼고 측정 회차만 집계한다")
    void excludesWarmupRoundsFromStatistics() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(ledgerFullReconciliationUseCase.reconcileAll()).thenReturn(List.of());

        LedgerFullReconciliationBaselineRunner.BaselineResult result =
                runner(4, 1).measure();

        assertThat(result.warmupElapsedMs()).hasSize(1);
        assertThat(result.measuredElapsedMs()).hasSize(3);
        assertThat(result.minMs()).isLessThanOrEqualTo(result.maxMs());
        assertThat(result.medianMs()).isBetween((double) result.minMs(), (double) result.maxMs());
    }

    @Test
    @DisplayName("회차별 불일치 건수를 남긴다")
    void recordsMismatchCountPerRound() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(ledgerFullReconciliationUseCase.reconcileAll())
                .thenReturn(List.of())
                .thenReturn(List.of(new LedgerReconciliationMismatch(1L, 100L, 90L)));

        LedgerFullReconciliationBaselineRunner.BaselineResult result =
                runner(2, 0).measure();

        assertThat(result.mismatchCounts()).containsExactly(0, 1);
    }

    @Test
    @DisplayName("다른 대사가 락을 쥐고 있으면 대사를 돌리지 않고 실패한다 — 0초를 측정값으로 남기지 않는다")
    void failsWithoutMeasuringWhenLockIsHeld() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(false);

        assertThatThrownBy(() -> runner(3, 1).measure()).isInstanceOf(IllegalStateException.class);

        verify(ledgerFullReconciliationUseCase, never()).reconcileAll();
        verify(batchExecutionLockPort, never()).release(JOB_NAME);
    }

    @Test
    @DisplayName("대사 중 예외가 나도 락을 풀고 예외를 그대로 던진다")
    void releasesLockWhenReconciliationFails() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(ledgerFullReconciliationUseCase.reconcileAll()).thenThrow(new IllegalStateException("DB 연결 끊김"));

        assertThatThrownBy(() -> runner(3, 1).measure()).hasMessage("DB 연결 끊김");

        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    private LedgerFullReconciliationBaselineRunner runner(int rounds, int warmupRounds) {
        return new LedgerFullReconciliationBaselineRunner(
                ledgerFullReconciliationUseCase,
                batchExecutionLockPort,
                new LedgerFullReconciliationBaselineProperties(rounds, warmupRounds));
    }
}
