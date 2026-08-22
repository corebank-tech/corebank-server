package com.shinhan.corebank.scheduledtransfer.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferBatchQueryPort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledTransferBatchServiceTest {

    @Mock
    ScheduledTransferBatchQueryPort scheduledTransferBatchQueryPort;

    @Mock
    ScheduledTransferBatchItemProcessor scheduledTransferBatchItemProcessor;

    @InjectMocks
    ScheduledTransferBatchService scheduledTransferBatchService;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 15);

    private ScheduledTransfer scheduledTransfer(Long id, ScheduledTransferStatus status) {
        return ScheduledTransfer.reconstitute(
                id, 1L, 2L, "088", "110987654321", "홍길동", 10_000L, DATE,
                "메모", "받는메모", status, null, LocalDateTime.of(2026, 1, 1, 0, 0), null, null, null);
    }

    @Test
    @DisplayName("대상 각 건에 대해 claim 후 completeProcessing을 순서대로 호출한다")
    void executeDaily_processesEachTarget() {
        ScheduledTransfer target = scheduledTransfer(10L, ScheduledTransferStatus.WAITING);
        when(scheduledTransferBatchQueryPort.findDueForExecution(DATE)).thenReturn(List.of(target));
        when(scheduledTransferBatchItemProcessor.claim(10L)).thenReturn(true);

        scheduledTransferBatchService.executeDaily(DATE);

        var inOrder = inOrder(scheduledTransferBatchItemProcessor);
        inOrder.verify(scheduledTransferBatchItemProcessor).claim(10L);
        inOrder.verify(scheduledTransferBatchItemProcessor).completeProcessing(target, DATE);
    }

    @Test
    @DisplayName("claim()이 false면(이미 다른 실행이 선점) completeProcessing()을 호출하지 않는다")
    void executeDaily_claimFails_skipsCompleteProcessing() {
        ScheduledTransfer target = scheduledTransfer(10L, ScheduledTransferStatus.WAITING);
        when(scheduledTransferBatchQueryPort.findDueForExecution(DATE)).thenReturn(List.of(target));
        when(scheduledTransferBatchItemProcessor.claim(10L)).thenReturn(false);

        scheduledTransferBatchService.executeDaily(DATE);

        verify(scheduledTransferBatchItemProcessor, never()).completeProcessing(any(), any());
    }

    @Test
    @DisplayName("claim()이 예외를 던져도 completeProcessing()을 호출하지 않고 다음 건으로 넘어간다")
    void executeDaily_claimThrows_skipsCompleteProcessingAndContinues() {
        ScheduledTransfer failing = scheduledTransfer(10L, ScheduledTransferStatus.WAITING);
        ScheduledTransfer succeeding = scheduledTransfer(11L, ScheduledTransferStatus.WAITING);
        when(scheduledTransferBatchQueryPort.findDueForExecution(DATE)).thenReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("DB 연결 장애")).when(scheduledTransferBatchItemProcessor).claim(10L);
        when(scheduledTransferBatchItemProcessor.claim(11L)).thenReturn(true);

        scheduledTransferBatchService.executeDaily(DATE);

        verify(scheduledTransferBatchItemProcessor, never()).completeProcessing(eq(failing), any());
        verify(scheduledTransferBatchItemProcessor).completeProcessing(succeeding, DATE);
    }

    @Test
    @DisplayName("completeProcessing()이 실패해도 배치 전체가 멈추지 않고 다음 건을 계속 처리한다")
    void executeDaily_completeProcessingFailure_continuesToNextItem() {
        ScheduledTransfer failing = scheduledTransfer(10L, ScheduledTransferStatus.WAITING);
        ScheduledTransfer succeeding = scheduledTransfer(11L, ScheduledTransferStatus.WAITING);
        when(scheduledTransferBatchQueryPort.findDueForExecution(DATE)).thenReturn(List.of(failing, succeeding));
        when(scheduledTransferBatchItemProcessor.claim(10L)).thenReturn(true);
        when(scheduledTransferBatchItemProcessor.claim(11L)).thenReturn(true);
        doThrow(new RuntimeException("이체 실행 중 장애"))
                .when(scheduledTransferBatchItemProcessor).completeProcessing(failing, DATE);

        scheduledTransferBatchService.executeDaily(DATE);

        verify(scheduledTransferBatchItemProcessor).completeProcessing(succeeding, DATE);
    }

    @Test
    @DisplayName("대상이 없으면 아무 것도 처리하지 않는다")
    void executeDaily_noTargets_doesNothing() {
        when(scheduledTransferBatchQueryPort.findDueForExecution(DATE)).thenReturn(List.of());

        scheduledTransferBatchService.executeDaily(DATE);

        verify(scheduledTransferBatchItemProcessor, never()).claim(any());
        verify(scheduledTransferBatchItemProcessor, never()).completeProcessing(any(), any());
    }

    @Test
    @DisplayName("PROCESSING에 멈춘 건 각각에 대해 reconcileStuckExecution을 호출한다")
    void reconcileStuckExecutions_processesEachStuckItem() {
        ScheduledTransfer stuck = scheduledTransfer(20L, ScheduledTransferStatus.PROCESSING);
        when(scheduledTransferBatchQueryPort.findAllProcessing()).thenReturn(List.of(stuck));

        scheduledTransferBatchService.reconcileStuckExecutions(DATE);

        verify(scheduledTransferBatchItemProcessor).reconcileStuckExecution(stuck);
    }

    @Test
    @DisplayName("한 건의 재확정 실패가 나머지 건 재확정을 막지 않는다")
    void reconcileStuckExecutions_oneFailureDoesNotBlockOthers() {
        ScheduledTransfer failing = scheduledTransfer(20L, ScheduledTransferStatus.PROCESSING);
        ScheduledTransfer succeeding = scheduledTransfer(21L, ScheduledTransferStatus.PROCESSING);
        when(scheduledTransferBatchQueryPort.findAllProcessing()).thenReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("조회 장애"))
                .when(scheduledTransferBatchItemProcessor).reconcileStuckExecution(failing);

        scheduledTransferBatchService.reconcileStuckExecutions(DATE);

        verify(scheduledTransferBatchItemProcessor).reconcileStuckExecution(succeeding);
    }
}
