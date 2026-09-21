package com.shinhan.corebank.transfer.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationUseCase;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerReconciliationBatchServiceTest {

    private static final String JOB_NAME = "LEDGER_RECONCILIATION_BATCH";
    private static final LocalDate DATE = LocalDate.of(2026, 8, 9);

    @Mock
    LedgerReconciliationUseCase ledgerReconciliationUseCase;

    @Mock
    BatchExecutionLockPort batchExecutionLockPort;

    private LedgerReconciliationBatchService service() {
        return new LedgerReconciliationBatchService(ledgerReconciliationUseCase, batchExecutionLockPort);
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
}
