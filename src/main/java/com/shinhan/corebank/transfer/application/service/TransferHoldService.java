package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.api.TransferHoldExecutionResult;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.application.port.out.TransferHoldPort;
import com.shinhan.corebank.transfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferHold;
import com.shinhan.corebank.transfer.domain.TransferHoldStatus;
import com.shinhan.corebank.transfer.domain.TransferSourceType;
import com.shinhan.corebank.transfer.domain.TransferType;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 이체 보류의 실행·거절·취소(transfer_seam.md §9-4).
 *
 * 선점이 먼저 커밋돼야 execute()가 EXECUTING을 보므로 트랜잭션을 걸지 않는다. 중간에 멈춘 건은 resume()이 마무리한다.
 */
@Service
@RequiredArgsConstructor
public class TransferHoldService implements TransferHoldUseCase {

    private final TransferHoldPort transferHoldPort;
    private final TransferExecutionUseCase transferExecutionUseCase;
    private final TransferLookupPort transferLookupPort;
    private final Clock clock;

    @Override
    public TransferHoldExecutionResult release(long transferHoldId, long actorId) {
        return releaseAs(transferHoldId, actorId);
    }

    /** 해제 시각이 된 보류를 스케줄러가 실행한다. 실행 주체가 없어 actor_id는 비워 둔다. */
    public TransferHoldExecutionResult releaseAsSystem(long transferHoldId) {
        return releaseAs(transferHoldId, null);
    }

    /** EXECUTING에서 멈춘 보류를 다시 실행해 결과를 남긴다. 이미 실행됐으면 그 결과를 재생한다. */
    public TransferHoldExecutionResult resume(long transferHoldId) {
        TransferHold hold = load(transferHoldId);
        if (hold.getStatus() != TransferHoldStatus.EXECUTING) {
            throw new BusinessException(TransferErrorCode.TRANSFER_HOLD_ALREADY_CLOSED);
        }
        return executeAndFinish(hold);
    }

    @Override
    public void reject(long transferHoldId, long actorId, String reasonCode) {
        if (!transferHoldPort.close(transferHoldId, TransferHoldStatus.REJECTED, actorId, reasonCode, now())) {
            load(transferHoldId);
            throw new BusinessException(TransferErrorCode.TRANSFER_HOLD_ALREADY_CLOSED);
        }
    }

    // 없는 건과 남의 건을 같은 오류로 돌려준다. 보류 ID로 다른 고객의 보류 존재를 알아내지 못하게 한다.
    @Override
    public void cancel(long transferHoldId, long customerId) {
        transferHoldPort
                .findById(transferHoldId)
                .filter(hold -> hold.getCustomerId() == customerId)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.TRANSFER_HOLD_NOT_FOUND));
        if (!transferHoldPort.close(transferHoldId, TransferHoldStatus.CANCELLED, customerId, null, now())) {
            throw new BusinessException(TransferErrorCode.TRANSFER_HOLD_ALREADY_CLOSED);
        }
    }

    private TransferHoldExecutionResult releaseAs(long transferHoldId, Long actorId) {
        if (transferHoldPort.markExecuting(transferHoldId, actorId, now())) {
            return executeAndFinish(load(transferHoldId));
        }
        TransferHold hold = load(transferHoldId);
        if (hold.getStatus() == TransferHoldStatus.EXECUTED || hold.getStatus() == TransferHoldStatus.FAILED) {
            return previousResult(hold);
        }
        throw new BusinessException(TransferErrorCode.TRANSFER_HOLD_ALREADY_CLOSED);
    }

    // execute()가 예외를 던지면 기록하지 않고 전파한다. 보류는 EXECUTING으로 남아 resume() 대상이 된다.
    private TransferHoldExecutionResult executeAndFinish(TransferHold hold) {
        TransferResult result = transferExecutionUseCase.execute(toCommand(hold));
        boolean succeeded = result.status() == ProcessResultStatus.SUCCESS;
        transferHoldPort.finishExecution(
                hold.getTransferHoldId(),
                succeeded ? TransferHoldStatus.EXECUTED : TransferHoldStatus.FAILED,
                findTransferId(result.transactionNumber()),
                succeeded ? null : result.errorCode(),
                now());
        return new TransferHoldExecutionResult(result.status(), result.transactionNumber(), result.errorCode());
    }

    // 이체 행 없이 거부된 FAILED는 남긴 오류코드만 돌려준다
    private TransferHoldExecutionResult previousResult(TransferHold hold) {
        return transferLookupPort
                .findBySourceAndExecutionDate(
                        TransferSourceType.HELD,
                        hold.getTransferHoldId(),
                        hold.getHeldAt().toLocalDate())
                .map(result -> new TransferHoldExecutionResult(
                        result.status(), result.transactionNumber(), result.errorCode()))
                .orElseGet(
                        () -> new TransferHoldExecutionResult(ProcessResultStatus.ERROR, null, hold.getReasonCode()));
    }

    // 채번 전에 거부되면 거래번호도 이체 행도 없다
    private Long findTransferId(String transactionNumber) {
        if (transactionNumber == null) {
            return null;
        }
        return transferLookupPort
                .findByTransactionNumber(transactionNumber)
                .map(Transfer::getTransferId)
                .orElse(null);
    }

    private static TransferCommand toCommand(TransferHold hold) {
        return TransferCommand.builder()
                .customerId(hold.getCustomerId())
                .withdrawalAccountId(hold.getWithdrawalAccountId())
                .depositAccountNumber(hold.getDepositAccountNumber())
                .amount(hold.getAmount())
                .transferType(TransferType.IMMEDIATE)
                .channel(hold.getChannel())
                .myPassbookMemo(hold.getMyPassbookMemo())
                .recipientPassbookMemo(hold.getRecipientPassbookMemo())
                .sourceId(hold.getTransferHoldId())
                .executionDate(hold.getHeldAt().toLocalDate())
                .flowId(hold.getFlowId())
                .build();
    }

    private TransferHold load(long transferHoldId) {
        return transferHoldPort
                .findById(transferHoldId)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.TRANSFER_HOLD_NOT_FOUND));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
