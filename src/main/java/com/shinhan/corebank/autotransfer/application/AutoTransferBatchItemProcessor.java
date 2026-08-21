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
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Component
@RequiredArgsConstructor
// 자동이체를 실제로 실행
public class AutoTransferBatchItemProcessor {
    private static final Logger log = LoggerFactory.getLogger(AutoTransferBatchItemProcessor.class);

    // 배치는 HTTP 요청이 아니라 클라이언트 IP가 없다 - "서버 자신이 트리거함"을 나타내는 관용적 루프백 주소.
    private static final String SYSTEM_REQUEST_IP = "127.0.0.1";
    private static final int CONSECUTIVE_FAILURE_THRESHOLD = 3;


    private final AutoTransferExecutionPersistencePort autoTransferExecutionPersistencePort;
    private final AutoTransferPersistencePort autoTransferPersistencePort;
    private final TransferExecutionUseCase transferExecutionUseCase;
    private final AuditLogService auditLogService;
    private final TransferLookupPort transferLookupPort;

    // DB에 지금부터 처리 시작 남
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AutoTransferExecution saveProcessing(AutoTransfer autoTransfer) {
        // executionDate는 배치가 도는 날이 아니라 이 회차의 논리적 실행일(nextExecutionDate)이어야
        // completeProcessing()이 만드는 TransferCommand.executionDate와 같은 값을 가리켜, 멱등성
        // 사전조회가 재시도 날짜가 달라져도 같은 회차로 인식한다.
        AutoTransferExecution processing = AutoTransferExecution.processing(autoTransfer.getNextExecutionDate(), autoTransfer.getAmount(), LocalDateTime.now());
        return autoTransferExecutionPersistencePort.save(processing, autoTransfer.getAutoTransferId());
    }

    // 실제 이체 및 다음 실행일 계산
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeProcessing(AutoTransfer autoTransfer, AutoTransferExecution processingExecution, LocalDate date) {
        // saveProcessing()과 동일하게, 이 회차의 논리적 실행일(nextExecutionDate)을 써야 한다 -
        // 그래야 전날 후속처리 실패로 nextExecutionDate가 안 넘어가 다음날 배치가 같은 회차를
        // date=다음날로 재선택해도, 멱등성 사전조회가 첫 시도와 같은 키로 인식해 중복 송금을 막는다.
        LocalDate executionDate = autoTransfer.getNextExecutionDate();
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
                .executionDate(executionDate)
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
                    ? Map.of("autoTransferId", autoTransfer.getAutoTransferId(), "executionDate", executionDate)
                    : Map.of("autoTransferId", autoTransfer.getAutoTransferId(), "executionDate", executionDate, "errorCode", result.errorCode());
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
        } else if (lookup.get().status() == ProcessResultStatus.ERROR){
            execution.markError(lookup.get().errorMessage(), lookup.get().transactionNumber());
        } else {
            log.warn("재확정 배치가 예상치 못한 transfer 상태를 조회함 - autoTransferId={}, status={}",
                    autoTransfer.getAutoTransferId(), lookup.get().status());
            execution.markError("실행 중 확인 불가로 재확정 배치가 오류 처리함", null);
        }

        // claim 단계가 없는 reconcileStuckExecution() 전용 가드: findAllProcessing()으로 조회만 하고
        // 선점하지 않아서, 동시에 두 번 돌면 이 저장 시점에 가드가 필요하다. 0건이면 이미 다른 실행이 먼저 확정한 것.
        boolean confirmed = autoTransferExecutionPersistencePort.saveIfStillProcessing(execution);
        if (!confirmed) {
            log.info("이미 다른 재확정 실행이 먼저 확정함(중복 재확정 방어) - autoTransferId={}, executionId={}",
                    autoTransfer.getAutoTransferId(), execution.getExecutionId());
            return;
        }

        if (execution.getStatus() == ProcessResultStatus.ERROR) {
            List<AutoTransferExecution> recent = autoTransferExecutionPersistencePort
                    .findRecentByAutoTransferId(autoTransfer.getAutoTransferId(), CONSECUTIVE_FAILURE_THRESHOLD);
            boolean allFailed = recent.size() == CONSECUTIVE_FAILURE_THRESHOLD
                    && recent.stream().allMatch(e -> e.getStatus() == ProcessResultStatus.ERROR);
            if (allFailed) {
                log.warn("자동이체 연속 실패 감지 - autoTransferId={}, 연속실패건수={}",
                        autoTransfer.getAutoTransferId(), CONSECUTIVE_FAILURE_THRESHOLD);
            }
        }

        autoTransfer.advanceNextExecutionDate();
        if(autoTransfer.getNextExecutionDate().isAfter(autoTransfer.getEndDate())) {
            autoTransfer.expire(LocalDateTime.now());
        }
        autoTransferPersistencePort.save(autoTransfer);
    }
}
