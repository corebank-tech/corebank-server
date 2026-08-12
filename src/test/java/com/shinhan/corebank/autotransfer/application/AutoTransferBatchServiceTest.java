package com.shinhan.corebank.autotransfer.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferBatchQueryPort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AutoTransferBatchServiceTest {

    @Mock
    AutoTransferBatchQueryPort autoTransferBatchQueryPort;

    @Mock
    AutoTransferBatchItemProcessor autoTransferBatchItemProcessor;

    @InjectMocks
    AutoTransferBatchService autoTransferBatchService;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 15);

    private AutoTransfer autoTransfer(Long autoTransferId) {
        return AutoTransfer.reconstitute(
                autoTransferId, 1L, 2L, "110987654321", "홍길동",
                10000L, 1, 15,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), DATE,
                "메모", "받는메모", AutoTransferStatus.NORMAL,
                LocalDateTime.of(2026, 1, 1, 0, 0), null, LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private AutoTransferExecution processingExecution() {
        return AutoTransferExecution.processing(DATE, 10000L, LocalDateTime.now());
    }

    @Test
    @DisplayName("대상 각 건에 대해 saveProcessing 후 completeProcessing을 순서대로 호출한다")
    void executeDaily_processesEachTarget() {
        AutoTransfer target = autoTransfer(10L);
        when(autoTransferBatchQueryPort.findDueForExecution(DATE)).thenReturn(List.of(target));
        AutoTransferExecution saved = processingExecution();
        when(autoTransferBatchItemProcessor.saveProcessing(eq(target), eq(DATE))).thenReturn(saved);

        autoTransferBatchService.executeDaily(DATE);

        verify(autoTransferBatchItemProcessor).saveProcessing(target, DATE);
        verify(autoTransferBatchItemProcessor).completeProcessing(target, saved, DATE);
    }

    @Test
    @DisplayName("saveProcessing()이 uk_ate_dup 위반이면 이미 처리된 것으로 보고 completeProcessing()을 호출하지 않는다")
    void executeDaily_saveProcessingConstraintViolation_skipsCompleteProcessing() {
        AutoTransfer target = autoTransfer(10L);
        when(autoTransferBatchQueryPort.findDueForExecution(DATE)).thenReturn(List.of(target));
        RuntimeException rootCause = new RuntimeException(
                "Duplicate entry '10-2026-03-15' for key 'auto_transfer_execution.uk_ate_dup'");
        doThrow(new DataIntegrityViolationException("insert failed", rootCause))
                .when(autoTransferBatchItemProcessor).saveProcessing(target, DATE);

        autoTransferBatchService.executeDaily(DATE);

        verify(autoTransferBatchItemProcessor, never()).completeProcessing(any(), any(), any());
    }

    @Test
    @DisplayName("saveProcessing()이 uk_ate_dup이 아닌 다른 제약 위반으로 실패해도 completeProcessing()을 호출하지 않고 다음 건으로 넘어간다")
    void executeDaily_saveProcessingOtherConstraintViolation_skipsCompleteProcessingAndContinues() {
        AutoTransfer failing = autoTransfer(10L);
        AutoTransfer succeeding = autoTransfer(11L);
        when(autoTransferBatchQueryPort.findDueForExecution(DATE)).thenReturn(List.of(failing, succeeding));
        RuntimeException rootCause = new RuntimeException(
                "Cannot add or update a child row: a foreign key constraint fails (`fk_ate_auto_transfer`)");
        doThrow(new DataIntegrityViolationException("insert failed", rootCause))
                .when(autoTransferBatchItemProcessor).saveProcessing(failing, DATE);
        AutoTransferExecution saved = processingExecution();
        when(autoTransferBatchItemProcessor.saveProcessing(eq(succeeding), eq(DATE))).thenReturn(saved);

        autoTransferBatchService.executeDaily(DATE);

        verify(autoTransferBatchItemProcessor, never()).completeProcessing(eq(failing), any(), any());
        verify(autoTransferBatchItemProcessor).completeProcessing(succeeding, saved, DATE);
    }

    @Test
    @DisplayName("saveProcessing()이 유니크 제약 위반이 아닌 다른 이유로 실패해도 completeProcessing()을 호출하지 않고 다음 건으로 넘어간다")
    void executeDaily_saveProcessingOtherFailure_skipsCompleteProcessingAndContinues() {
        AutoTransfer failing = autoTransfer(10L);
        AutoTransfer succeeding = autoTransfer(11L);
        when(autoTransferBatchQueryPort.findDueForExecution(DATE)).thenReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("DB 연결 장애"))
                .when(autoTransferBatchItemProcessor).saveProcessing(failing, DATE);
        AutoTransferExecution saved = processingExecution();
        when(autoTransferBatchItemProcessor.saveProcessing(eq(succeeding), eq(DATE))).thenReturn(saved);

        autoTransferBatchService.executeDaily(DATE);

        verify(autoTransferBatchItemProcessor, never()).completeProcessing(eq(failing), any(), any());
        verify(autoTransferBatchItemProcessor).completeProcessing(succeeding, saved, DATE);
    }

    @Test
    @DisplayName("completeProcessing()이 실패해도 배치 전체가 멈추지 않고 다음 건을 계속 처리한다")
    void executeDaily_completeProcessingFailure_continuesToNextItem() {
        AutoTransfer failing = autoTransfer(10L);
        AutoTransfer succeeding = autoTransfer(11L);
        when(autoTransferBatchQueryPort.findDueForExecution(DATE)).thenReturn(List.of(failing, succeeding));
        AutoTransferExecution savedFailing = processingExecution();
        AutoTransferExecution savedSucceeding = processingExecution();
        when(autoTransferBatchItemProcessor.saveProcessing(eq(failing), eq(DATE))).thenReturn(savedFailing);
        when(autoTransferBatchItemProcessor.saveProcessing(eq(succeeding), eq(DATE))).thenReturn(savedSucceeding);
        doThrow(new RuntimeException("이체 실행 중 장애"))
                .when(autoTransferBatchItemProcessor).completeProcessing(failing, savedFailing, DATE);

        autoTransferBatchService.executeDaily(DATE);

        verify(autoTransferBatchItemProcessor).completeProcessing(succeeding, savedSucceeding, DATE);
    }

    @Test
    @DisplayName("대상이 없으면 아무 것도 처리하지 않는다")
    void executeDaily_noTargets_doesNothing() {
        when(autoTransferBatchQueryPort.findDueForExecution(DATE)).thenReturn(List.of());

        autoTransferBatchService.executeDaily(DATE);

        verify(autoTransferBatchItemProcessor, never()).saveProcessing(any(), any());
        verify(autoTransferBatchItemProcessor, never()).completeProcessing(any(), any(), any());
    }
}
