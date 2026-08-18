package com.shinhan.corebank.autotransfer.application;

import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferExecutionPersistencePort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferPersistencePort;
import com.shinhan.corebank.autotransfer.application.port.out.StuckExecution;
import com.shinhan.corebank.autotransfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.autotransfer.application.port.out.TransferLookupResult;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;


@Component
@RequiredArgsConstructor
// 자동이체를 실제로 실행
public class AutoTransferBatchItemProcessor {
    private static final Logger log = LoggerFactory.getLogger(AutoTransferBatchItemProcessor.class);

    // 배치는 HTTP 요청이 아니라 클라이언트 IP가 없다 - "서버 자신이 트리거함"을 나타내는 관용적 루프백 주소.
    private static final String SYSTEM_REQUEST_IP = "127.0.0.1";

    private final AutoTransferExecutionPersistencePort autoTransferExecutionPersistencePort;
    private final AutoTransferPersistencePort autoTransferPersistencePort;
    private final TransferExecutionUseCase transferExecutionUseCase;
    private final AuditLogService auditLogService;
    private final TransferLookupPort transferLookupPort;

    // DB에 지금부터 처리 시작 남
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AutoTransferExecution saveProcessing(AutoTransfer autoTransfer, LocalDate date) {
        AutoTransferExecution processing = AutoTransferExecution.processing(date, autoTransfer.getAmount(), LocalDateTime.now());
        return autoTransferExecutionPersistencePort.save(processing, autoTransfer.getAutoTransferId());
    }

    // 실제 이체 및 다음 실행일 계산
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeProcessing(AutoTransfer autoTransfer, AutoTransferExecution processingExecution, LocalDate date) {
        TransferCommand command = TransferCommand.builder()
                .customerId(autoTransfer.getCustomerId())
                .withdrawalAccountId(autoTransfer.getWithdrawalAccountId())
                .depositAccountNumber(autoTransfer.getDepositAccountNumber())
                .amount(autoTransfer.getAmount())
                .transferType(TransferType.AUTO)
                .channel(TransferChannel.BT)
                .myPassbookMemo(autoTransfer.getMyPassbookMemo())
                .recipientPassbookMemo(autoTransfer.getRecipientPassbookMemo())
                .sourceId(autoTransfer.getAutoTransferId())
                .build();

        TransferResult result = transferExecutionUseCase.execute(command);

        if (!result.status().isConfirmed()) {
            throw new IllegalStateException("이체 실행 결과가 PROCESSING으로 미확정 - autoTransferId=%d, date=%s"
                    .formatted(autoTransfer.getAutoTransferId(), date));
        }
        if (result.status() == ProcessResultStatus.SUCCESS) {
            processingExecution.markSuccess(result.transactionNumber());
        } else {
            processingExecution.markError(result.errorMessage(), result.transactionNumber());
            log.warn("자동이체 실행 실패 - autoTransferId={}, date={}, errorCode={}, errorMessage={}",
                    autoTransfer.getAutoTransferId(), date, result.errorCode(), result.errorMessage());
        }
        autoTransferExecutionPersistencePort.save(processingExecution, autoTransfer.getAutoTransferId());

        autoTransfer.advanceNextExecutionDate();
        if (autoTransfer.getNextExecutionDate().isAfter(autoTransfer.getEndDate())) {
            autoTransfer.expire(LocalDateTime.now());
        }
        autoTransferPersistencePort.save(autoTransfer);

        if (StringUtils.hasText(processingExecution.getTransactionNumber())) {
            boolean succeeded = result.status() == ProcessResultStatus.SUCCESS;
            Map<String, Object> detail = succeeded
                    ? Map.of("autoTransferId", autoTransfer.getAutoTransferId(), "executionDate", date)
                    : Map.of("autoTransferId", autoTransfer.getAutoTransferId(), "executionDate", date, "errorCode", result.errorCode());
            auditLogService.record(autoTransfer.getCustomerId(), processingExecution.getTransactionNumber(),
                    AuditEventType.AUTO_TRANSFER, SYSTEM_REQUEST_IP, succeeded, detail);
        }
    }

    // transfer 테이블에 실제 거래가 있었는지만 확인해서 확정
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileStuckExecution(StuckExecution stuckExecution) {
        AutoTransfer autoTransfer = stuckExecution.autoTransfer();
        AutoTransferExecution execution = stuckExecution.execution();

        Optional<TransferLookupResult> lookup = transferLookupPort.findBySourceAndDate(autoTransfer.getAutoTransferId(), execution.getExecutionDate());

        if(lookup.isEmpty()) {
            execution.markError("실행 중 확인 불가로 재확정 배치가 오류 처리함", null);
        } else if (lookup.get().status() == ProcessResultStatus.SUCCESS) {
            execution.markSuccess(lookup.get().transactionNumber());
        } else {
            execution.markError(lookup.get().errorMessage(), lookup.get().transactionNumber());
        }

        autoTransferExecutionPersistencePort.save(execution, autoTransfer.getAutoTransferId());
    }
}
