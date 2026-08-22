package com.shinhan.corebank.scheduledtransfer.application.service;

import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferPersistencePort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.TransferLookupResult;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
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
// 예약이체를 실제로 실행
public class ScheduledTransferBatchItemProcessor {
    private static final Logger log = LoggerFactory.getLogger(ScheduledTransferBatchItemProcessor.class);

    // 배치는 HTTP 요청이 아니라 클라이언트 IP가 없다 - "서버 자신이 트리거함"을 나타내는 관용적 루프백 주소.
    private static final String SYSTEM_REQUEST_IP = "127.0.0.1";

    private final ScheduledTransferPersistencePort scheduledTransferPersistencePort;
    private final TransferExecutionUseCase transferExecutionUseCase;
    private final AuditLogService auditLogService;
    private final TransferLookupPort transferLookupPort;

    // WAITING -> PROCESSING 원자적 선점 (REQ-SCD-013 멱등성 방어)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(Long scheduledTransferId) {
        return scheduledTransferPersistencePort.claimForProcessing(scheduledTransferId);
    }

    // 실제 이체 실행 및 결과 확정
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeProcessing(ScheduledTransfer scheduledTransfer, LocalDate date) {
        TransferCommand command = TransferCommand.builder()
                .customerId(scheduledTransfer.getCustomerId())
                .withdrawalAccountId(scheduledTransfer.getWithdrawalAccountId())
                .depositAccountNumber(scheduledTransfer.getPayeeAccountNumber())
                .amount(scheduledTransfer.getAmount())
                .transferType(TransferType.SCHEDULED)
                .channel(TransferChannel.BT)
                .myPassbookMemo(scheduledTransfer.getMyPassbookMemo())
                .recipientPassbookMemo(scheduledTransfer.getRecipientPassbookMemo())
                .sourceId(scheduledTransfer.getScheduledTransferId())
                .executionDate(scheduledTransfer.getScheduledDate())
                .build();

        TransferResult result = transferExecutionUseCase.execute(command);

        if (!result.status().isConfirmed()) {
            throw new IllegalStateException("이체 실행 결과가 PROCESSING으로 미확정 - scheduledTransferId=%d, date=%s"
                    .formatted(scheduledTransfer.getScheduledTransferId(), date));
        }

        LocalDateTime executedAt = LocalDateTime.now();
        if (result.status() == ProcessResultStatus.SUCCESS) {
            scheduledTransfer.markSuccess(result.transactionNumber(), executedAt);
        } else {
            scheduledTransfer.markFailed(result.errorMessage(), result.transactionNumber(), executedAt);
            log.warn("예약이체 실행 실패 - scheduledTransferId={}, date={}, errorCode={}, errorMessage={}",
                    scheduledTransfer.getScheduledTransferId(), date, result.errorCode(), result.errorMessage());
        }
        scheduledTransferPersistencePort.save(scheduledTransfer);

        recordAudit(scheduledTransfer, date, result.status() == ProcessResultStatus.SUCCESS, result.errorCode());
    }

    // transfer 테이블에 실제 거래가 있었는지만 확인해서 확정 (PROCESSING에 멈춘 건 재확정)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileStuckExecution(ScheduledTransfer scheduledTransfer) {
        Optional<TransferLookupResult> lookup = transferLookupPort.findBySourceAndDate(
                scheduledTransfer.getScheduledTransferId(), scheduledTransfer.getScheduledDate());

        LocalDateTime now = LocalDateTime.now();
        if (lookup.isEmpty()) {
            scheduledTransfer.markFailed("실행 중 확인 불가로 재확정 배치가 오류 처리함", null, now);
        } else if (lookup.get().status() == ProcessResultStatus.SUCCESS) {
            scheduledTransfer.markSuccess(lookup.get().transactionNumber(), now);
        } else if (lookup.get().status() == ProcessResultStatus.ERROR) {
            scheduledTransfer.markFailed(lookup.get().errorMessage(), lookup.get().transactionNumber(), now);
        } else {
            log.warn("재확정 배치가 예상치 못한 transfer 상태를 조회함 - scheduledTransferId={}, status={}",
                    scheduledTransfer.getScheduledTransferId(), lookup.get().status());
            scheduledTransfer.markFailed("실행 중 확인 불가로 재확정 배치가 오류 처리함", null, now);
        }

        // claim()과 대칭되는 가드: 재확정 배치는 findAllProcessing()로 조회만 하고 선점 단계가 없어서,
        // 동시에 두 번 돌면 이 저장 시점에 가드가 필요하다. 0건이면 이미 다른 실행이 먼저 확정한 것.
        boolean confirmed = scheduledTransferPersistencePort.saveIfStillProcessing(scheduledTransfer);
        if (!confirmed) {
            log.info("이미 다른 재확정 실행이 먼저 확정함(중복 재확정 방어) - scheduledTransferId={}",
                    scheduledTransfer.getScheduledTransferId());
            return;
        }

        recordAudit(scheduledTransfer, scheduledTransfer.getScheduledDate(),
                scheduledTransfer.getStatus() == ScheduledTransferStatus.SUCCESS, null);
    }

    private void recordAudit(ScheduledTransfer scheduledTransfer, LocalDate date, boolean succeeded, String errorCode) {
        if (!StringUtils.hasText(scheduledTransfer.getTransactionNumber())) {
            return;
        }
        Map<String, Object> detail = succeeded
                ? Map.of("scheduledTransferId", scheduledTransfer.getScheduledTransferId(), "executionDate", date)
                : errorCode != null
                    ? Map.of("scheduledTransferId", scheduledTransfer.getScheduledTransferId(), "executionDate", date, "errorCode", errorCode)
                    : Map.of("scheduledTransferId", scheduledTransfer.getScheduledTransferId(), "executionDate", date);
        auditLogService.record(scheduledTransfer.getCustomerId(), scheduledTransfer.getTransactionNumber(),
                AuditEventType.SCHEDULED_TRANSFER, SYSTEM_REQUEST_IP, succeeded, detail);
    }
}
