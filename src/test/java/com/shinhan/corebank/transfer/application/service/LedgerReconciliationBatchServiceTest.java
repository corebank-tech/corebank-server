package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import com.shinhan.corebank.batch.api.CobStepHaltException;
import com.shinhan.corebank.transfer.application.port.in.LedgerFullReconciliationUseCase;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatch;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationUseCase;
import com.shinhan.corebank.transfer.application.port.out.LedgerReconciliationMismatchPort;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class LedgerReconciliationBatchServiceTest {

    private static final String JOB_NAME = "LEDGER_RECONCILIATION_BATCH";
    private static final String DAILY_BATCH_JOB_NAME = "DAILY_TRANSFER_BATCH";
    private static final LocalDate DATE = LocalDate.of(2026, 8, 9);
    private static final int COB_HARD_THRESHOLD = 10;
    private static final String SOFT_MARKER = "[LEDGER_RECONCILIATION_SOFT]";
    private static final String HARD_MARKER = "[LEDGER_RECONCILIATION_HARD]";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime DETECTED_AT = LocalDateTime.of(2026, 8, 10, 0, 30);
    private static final Clock FIXED_CLOCK = Clock.fixed(DETECTED_AT.atZone(KST).toInstant(), KST);
    private static final Logger SERVICE_LOGGER =
            (Logger) LoggerFactory.getLogger(LedgerReconciliationBatchService.class);

    @Mock
    LedgerReconciliationUseCase ledgerReconciliationUseCase;

    @Mock
    LedgerFullReconciliationUseCase ledgerFullReconciliationUseCase;

    @Mock
    BatchExecutionLockPort batchExecutionLockPort;

    @Mock
    LedgerReconciliationMismatchPort ledgerReconciliationMismatchPort;

    // 콘솔 문자열이 아니라 로그 이벤트를 받아야 레벨(WARN/ERROR)을 정확히 비교할 수 있다
    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();

    @BeforeEach
    void attachLogAppender() {
        logAppender.start();
        SERVICE_LOGGER.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        SERVICE_LOGGER.detachAppender(logAppender);
    }

    private List<ILoggingEvent> logsWith(String marker) {
        return logAppender.list.stream()
                .filter(event -> event.getFormattedMessage().contains(marker))
                .toList();
    }

    private LedgerReconciliationBatchService service() {
        // 대기 로직을 빠르게 돌리도록 poll 간격 1ms, 최대 대기 5ms(=5회 재확인)로 좁힌다.
        return new LedgerReconciliationBatchService(
                ledgerReconciliationUseCase,
                ledgerFullReconciliationUseCase,
                batchExecutionLockPort,
                ledgerReconciliationMismatchPort,
                FIXED_CLOCK,
                Duration.ofMillis(1),
                Duration.ofMillis(5),
                COB_HARD_THRESHOLD);
    }

    private static List<LedgerReconciliationMismatch> mismatches(int count) {
        return LongStream.rangeClosed(1, count)
                .mapToObj(accountId -> new LedgerReconciliationMismatch(accountId, 1_000L, 900L))
                .toList();
    }

    @Test
    @DisplayName("COB 대사는 run()·runFull()과 같은 락을 잡고 전수 대사를 실행한 뒤 반납한다")
    void runInCob_acquiresSameLock_reconcilesAll_thenReleases() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(ledgerFullReconciliationUseCase.reconcileAll()).thenReturn(List.of());

        service().runInCob(DATE);

        InOrder inOrder = inOrder(batchExecutionLockPort, ledgerFullReconciliationUseCase);
        inOrder.verify(batchExecutionLockPort).tryAcquire(JOB_NAME);
        inOrder.verify(ledgerFullReconciliationUseCase).reconcileAll();
        inOrder.verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("COB 대사가 락을 못 잡으면 대사를 건너뛰고 SKIPPED를 ERROR로 남기며 release()는 부르지 않는다")
    void runInCob_lockNotAcquired_skipsAndLogsError() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(false);

        service().runInCob(DATE);

        verify(ledgerFullReconciliationUseCase, never()).reconcileAll();
        verify(batchExecutionLockPort, never()).release(JOB_NAME);
        verifyNoInteractions(ledgerReconciliationMismatchPort);
        assertThat(logsWith("[LEDGER_RECONCILIATION_SKIPPED]"))
                .singleElement()
                .extracting(ILoggingEvent::getLevel)
                .isEqualTo(Level.ERROR);
    }

    @Test
    @DisplayName("COB 대사 중 예외는 삼키지 않고 러너까지 전파하되 락은 반납한다 — 나중에 켤 CobStepHaltException 경로")
    void runInCob_reconcileFails_propagatesException_lockStillReleased() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        IllegalStateException failure = new IllegalStateException("boom");
        doThrow(failure).when(ledgerFullReconciliationUseCase).reconcileAll();

        assertThatThrownBy(() -> service().runInCob(DATE)).isSameAs(failure);

        verify(batchExecutionLockPort).release(JOB_NAME);
        verifyNoInteractions(ledgerReconciliationMismatchPort);
    }

    @Test
    @DisplayName("COB 대사는 불일치를 그 영업일 목록으로 판정보다 먼저 저장한다 — hard에서 마감을 멈춰도 목록이 남아야 한다")
    void runInCob_savesMismatchesForCobDate_beforeJudging() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        List<LedgerReconciliationMismatch> found = mismatches(COB_HARD_THRESHOLD + 1);
        when(ledgerFullReconciliationUseCase.reconcileAll()).thenReturn(found);
        // 저장이 불린 순간까지 판정 로그가 찍혔는지 기록해 둔다
        List<ILoggingEvent> judgeLogsAtSave = new ArrayList<>();
        doAnswer(invocation -> {
                    judgeLogsAtSave.addAll(logsWith(SOFT_MARKER));
                    judgeLogsAtSave.addAll(logsWith(HARD_MARKER));
                    return null;
                })
                .when(ledgerReconciliationMismatchPort)
                .replaceForBusinessDate(DATE, found, DETECTED_AT);

        assertThatThrownBy(() -> service().runInCob(DATE)).isInstanceOf(CobStepHaltException.class);

        verify(ledgerReconciliationMismatchPort).replaceForBusinessDate(DATE, found, DETECTED_AT);
        assertThat(judgeLogsAtSave).isEmpty();
        assertThat(logsWith(HARD_MARKER)).hasSize(1);
    }

    @Test
    @DisplayName("COB 대사 불일치가 0건이어도 저장을 불러 그 영업일에 남아 있던 행을 비운다")
    void runInCob_noMismatch_stillReplacesToClearThatDay() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(ledgerFullReconciliationUseCase.reconcileAll()).thenReturn(List.of());

        service().runInCob(DATE);

        verify(ledgerReconciliationMismatchPort).replaceForBusinessDate(DATE, List.of(), DETECTED_AT);
    }

    @Test
    @DisplayName("불일치 저장이 실패하면 삼키지 않고 러너까지 전파하되 락은 반납한다 — 삼키면 불일치가 조용히 사라진다")
    void runInCob_saveFails_propagatesException_lockStillReleased() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(ledgerFullReconciliationUseCase.reconcileAll()).thenReturn(mismatches(1));
        IllegalStateException failure = new IllegalStateException("db down");
        doThrow(failure).when(ledgerReconciliationMismatchPort).replaceForBusinessDate(any(), any(), any());

        assertThatThrownBy(() -> service().runInCob(DATE)).isSameAs(failure);

        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("COB 대사 불일치가 0건이면 soft·hard 로그를 남기지 않는다")
    void runInCob_noMismatch_logsNeitherSoftNorHard() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(ledgerFullReconciliationUseCase.reconcileAll()).thenReturn(List.of());

        service().runInCob(DATE);

        assertThat(logsWith(SOFT_MARKER)).isEmpty();
        assertThat(logsWith(HARD_MARKER)).isEmpty();
    }

    @Test
    @DisplayName("COB 대사 불일치가 임계와 같으면(soft) SOFT를 WARN으로 남기고 예외 없이 끝난다")
    void runInCob_mismatchesAtThreshold_soft_logsWarn() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(ledgerFullReconciliationUseCase.reconcileAll()).thenReturn(mismatches(COB_HARD_THRESHOLD));

        assertThatCode(() -> service().runInCob(DATE)).doesNotThrowAnyException();

        assertThat(logsWith(SOFT_MARKER))
                .singleElement()
                .extracting(ILoggingEvent::getLevel)
                .isEqualTo(Level.WARN);
        assertThat(logsWith(HARD_MARKER)).isEmpty();
    }

    @Test
    @DisplayName("COB 대사 불일치가 임계를 넘으면(hard) HARD를 ERROR로 남기고 CobStepHaltException으로 마감을 중단하며 락은 반납한다(#663)")
    void runInCob_mismatchesOverThreshold_hard_throwsHalt_andReleasesLock() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(ledgerFullReconciliationUseCase.reconcileAll()).thenReturn(mismatches(COB_HARD_THRESHOLD + 1));

        assertThatThrownBy(() -> service().runInCob(DATE))
                .isInstanceOf(CobStepHaltException.class)
                .hasMessageContaining("cobDate=" + DATE);

        assertThat(logsWith(HARD_MARKER))
                .singleElement()
                .extracting(ILoggingEvent::getLevel)
                .isEqualTo(Level.ERROR);
        assertThat(logsWith(SOFT_MARKER)).isEmpty();
        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("전수 대사도 같은 락을 잡고 실행한 뒤 반납한다 — 증분 대사와 동시에 돌지 않는다(#468)")
    void runFull_acquiresSameLock_reconcilesAll_thenReleases() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);

        service().runFull();

        InOrder inOrder = inOrder(batchExecutionLockPort, ledgerFullReconciliationUseCase);
        inOrder.verify(batchExecutionLockPort).tryAcquire(JOB_NAME);
        inOrder.verify(ledgerFullReconciliationUseCase).reconcileAll();
        inOrder.verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("락 선점에 실패하면(증분 대사가 아직 실행 중) 전수 대사를 건너뛴다")
    void runFull_lockNotAcquired_skips() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(false);

        service().runFull();

        verify(ledgerFullReconciliationUseCase, never()).reconcileAll();
        verify(batchExecutionLockPort, never()).release(JOB_NAME);
    }

    @Test
    @DisplayName("전수 대사 중 예외가 나도 락은 반납된다")
    void runFull_fails_lockStillReleased() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        doThrow(new IllegalStateException("boom"))
                .when(ledgerFullReconciliationUseCase)
                .reconcileAll();

        service().runFull();

        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("락 선점에 성공하면 대사를 실행하고 끝나면 락을 반납한다")
    void run_acquiresLock_reconciles_thenReleases() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);

        service().run(DATE);

        InOrder inOrder = inOrder(batchExecutionLockPort, ledgerReconciliationUseCase);
        inOrder.verify(batchExecutionLockPort).tryAcquire(JOB_NAME);
        inOrder.verify(ledgerReconciliationUseCase).reconcile(DATE);
        inOrder.verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("락 선점에 실패하면(이미 실행 중) 대사를 실행하지 않고 release()도 부르지 않는다")
    void run_lockNotAcquired_skipsReconcile() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(false);

        service().run(DATE);

        verify(ledgerReconciliationUseCase, never()).reconcile(any());
        verify(batchExecutionLockPort, never()).release(JOB_NAME);
    }

    @Test
    @DisplayName("대사 중 예외가 나도 락은 반납된다")
    void run_reconcileFails_lockStillReleased() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        doThrow(new IllegalStateException("boom"))
                .when(ledgerReconciliationUseCase)
                .reconcile(DATE);

        service().run(DATE);

        verify(ledgerReconciliationUseCase).reconcile(DATE);
        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("자동이체·예약이체 배치가 이미 끝나 있으면 곧바로 대사 락을 잡고 실행한다")
    void run_dailyBatchAlreadyFinished_proceedsImmediately() {
        when(batchExecutionLockPort.isRunning(DAILY_BATCH_JOB_NAME)).thenReturn(false);
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);

        service().run(DATE);

        verify(batchExecutionLockPort, times(1)).isRunning(DAILY_BATCH_JOB_NAME);
        verify(ledgerReconciliationUseCase).reconcile(DATE);
    }

    @Test
    @DisplayName("자동이체·예약이체 배치가 돌다가 대기 중 끝나면 재확인 후 대사를 실행한다")
    void run_dailyBatchFinishesDuringWait_proceedsAfterPolling() {
        when(batchExecutionLockPort.isRunning(DAILY_BATCH_JOB_NAME)).thenReturn(true, true, false);
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);

        service().run(DATE);

        verify(batchExecutionLockPort, times(3)).isRunning(DAILY_BATCH_JOB_NAME);
        verify(ledgerReconciliationUseCase).reconcile(DATE);
    }

    @Test
    @DisplayName("자동이체·예약이체 배치가 최대 대기시간 내내 안 끝나면 대사를 건너뛰고 락도 잡지 않는다")
    void run_dailyBatchNeverFinishes_skipsReconcileWithoutAcquiringLock() {
        when(batchExecutionLockPort.isRunning(DAILY_BATCH_JOB_NAME)).thenReturn(true);

        service().run(DATE);

        verify(batchExecutionLockPort, never()).tryAcquire(JOB_NAME);
        verify(ledgerReconciliationUseCase, never()).reconcile(any());
        verify(batchExecutionLockPort, never()).release(JOB_NAME);
    }
}
