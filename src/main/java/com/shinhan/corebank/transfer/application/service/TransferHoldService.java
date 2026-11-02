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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 이체 보류의 실행·거절·취소(transfer_seam.md §9-4).
 *
 * 선점이 먼저 커밋돼야 execute()가 EXECUTING을 보므로 트랜잭션을 걸지 않는다. 중간에 멈춘 건은 resume()이 마무리한다.
 *
 * 선점 시각(executing_at)이 선점의 표식이다. 결과는 그 표식으로 선점한 쪽만 기록한다(펜싱).
 */
@Service
@RequiredArgsConstructor
public class TransferHoldService implements TransferHoldUseCase {

    private final TransferHoldPort transferHoldPort;
    private final TransferExecutionUseCase transferExecutionUseCase;
    private final TransferLookupPort transferLookupPort;
    private final Clock clock;

    /** 관리자 승인(FDS). 승인으로 푸는 보류만 실행한다. 시각으로 푸는 보류는 TRF0310이다. */
    @Override
    public TransferHoldExecutionResult release(long transferHoldId, long actorId) {
        LocalDateTime claimedAt = now();
        if (transferHoldPort.markApprovedExecuting(transferHoldId, actorId, claimedAt)) {
            return executeAndFinish(load(transferHoldId), claimedAt);
        }
        return afterLostClaim(transferHoldId);
    }

    /** 스케줄러 해제. 시각으로 푸는 보류 중 해제 시각이 지난 것만 실행한다. 승인 대기·시각 전은 TRF0310이다. */
    public TransferHoldExecutionResult releaseAsSystem(long transferHoldId) {
        LocalDateTime claimedAt = now();
        if (transferHoldPort.markDueExecuting(transferHoldId, claimedAt)) {
            return executeAndFinish(load(transferHoldId), claimedAt);
        }
        return afterLostClaim(transferHoldId);
    }

    /**
     * stuckAfter보다 오래 EXECUTING인 보류를 다시 선점해 실행한다. 아직 실행 중인 건은 건드리지 않는다(TRF0311).
     *
     * 다시 선점하면 앞선 실행은 결과를 기록하지 못한다. 이체는 유니크 키라 다시 실행해도 기표는 한 번이다.
     */
    public TransferHoldExecutionResult resume(long transferHoldId, Duration stuckAfter) {
        LocalDateTime claimedAt = now();
        if (transferHoldPort.reclaimStuck(transferHoldId, claimedAt.minus(stuckAfter), claimedAt)) {
            return executeAndFinish(load(transferHoldId), claimedAt);
        }
        return afterLostClaim(transferHoldId);
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

    // 선점하지 못했다. 끝난 건은 앞선 결과를, 아직 HELD면 이 경로로 풀 수 없는 건(TRF0310), 그 밖은 TRF0311이다.
    private TransferHoldExecutionResult afterLostClaim(long transferHoldId) {
        TransferHold hold = load(transferHoldId);
        if (isFinished(hold)) {
            return previousResult(hold);
        }
        if (hold.getStatus() == TransferHoldStatus.HELD) {
            throw new BusinessException(TransferErrorCode.TRANSFER_HOLD_NOT_EXECUTABLE);
        }
        throw new BusinessException(TransferErrorCode.TRANSFER_HOLD_ALREADY_CLOSED);
    }

    /**
     * 실행하고 결과를 기록한다. 결과는 호출 반환값이 아니라 실제 이체 행으로 정해, 보류 상태가 이체와 어긋나지 않게 한다.
     *
     * 다른 곳이 다시 선점했으면 기록이 실패한다. 그때는 그쪽이 남긴 상태를 돌려준다. execute()가 예외를 던지면
     * 기록하지 않고 전파해 EXECUTING으로 남긴다(resume() 대상).
     */
    private TransferHoldExecutionResult executeAndFinish(TransferHold hold, LocalDateTime claimedAt) {
        TransferResult executed = transferExecutionUseCase.execute(toCommand(hold, claimedAt));
        TransferResult result = recordedTransfer(hold).orElse(executed);
        boolean succeeded = result.status() == ProcessResultStatus.SUCCESS;
        boolean recorded = transferHoldPort.finishExecution(
                hold.getTransferHoldId(),
                claimedAt,
                succeeded ? TransferHoldStatus.EXECUTED : TransferHoldStatus.FAILED,
                findTransferId(result.transactionNumber()),
                succeeded ? null : result.errorCode(),
                now());
        if (!recorded) {
            TransferHold latest = load(hold.getTransferHoldId());
            if (isFinished(latest)) {
                return previousResult(latest);
            }
        }
        return new TransferHoldExecutionResult(result.status(), result.transactionNumber(), result.errorCode());
    }

    private Optional<TransferResult> recordedTransfer(TransferHold hold) {
        return transferLookupPort.findBySourceAndExecutionDate(
                TransferSourceType.HELD,
                hold.getTransferHoldId(),
                hold.getHeldAt().toLocalDate());
    }

    private static boolean isFinished(TransferHold hold) {
        return hold.getStatus() == TransferHoldStatus.EXECUTED || hold.getStatus() == TransferHoldStatus.FAILED;
    }

    // 이체 행 없이 거부된 FAILED는 남긴 오류코드만 돌려준다
    private TransferHoldExecutionResult previousResult(TransferHold hold) {
        return recordedTransfer(hold)
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

    private static TransferCommand toCommand(TransferHold hold, LocalDateTime claimedAt) {
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
                .holdClaimedAt(claimedAt)
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
