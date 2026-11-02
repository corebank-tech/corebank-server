package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.application.port.out.TransferHoldPort;
import com.shinhan.corebank.transfer.domain.TransferHold;
import com.shinhan.corebank.transfer.domain.TransferHoldStatus;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferHoldPersistenceAdapter implements TransferHoldPort {

    private static final Set<TransferHoldStatus> EXECUTION_RESULTS =
            EnumSet.of(TransferHoldStatus.EXECUTED, TransferHoldStatus.FAILED);
    private static final Set<TransferHoldStatus> CLOSE_RESULTS =
            EnumSet.of(TransferHoldStatus.REJECTED, TransferHoldStatus.CANCELLED, TransferHoldStatus.EXPIRED);

    private final TransferHoldJpaRepository repository;

    @Override
    public TransferHold save(TransferHold transferHold) {
        return TransferHoldMapper.toDomain(repository.save(TransferHoldMapper.toEntity(transferHold)));
    }

    @Override
    public Optional<TransferHold> findById(long transferHoldId) {
        return repository.findById(transferHoldId).map(TransferHoldMapper::toDomain);
    }

    @Override
    public boolean markDueExecuting(long transferHoldId, LocalDateTime executingAt) {
        return repository.markDueExecuting(transferHoldId, executingAt) == 1;
    }

    @Override
    public boolean markApprovedExecuting(long transferHoldId, long actorId, LocalDateTime executingAt) {
        return repository.markApprovedExecuting(transferHoldId, actorId, executingAt) == 1;
    }

    @Override
    public boolean reclaimStuck(long transferHoldId, LocalDateTime staleBefore, LocalDateTime executingAt) {
        return repository.reclaimStuck(transferHoldId, staleBefore, executingAt) == 1;
    }

    @Override
    public boolean holdsClaim(long transferHoldId, LocalDateTime claimedAt) {
        return repository.findClaimForUpdate(transferHoldId, claimedAt).isPresent();
    }

    @Override
    public boolean finishExecution(
            long transferHoldId,
            LocalDateTime claimedAt,
            TransferHoldStatus result,
            Long transferId,
            String errorCode,
            LocalDateTime completedAt) {
        requireOneOf(EXECUTION_RESULTS, result);
        return repository.finishExecution(transferHoldId, claimedAt, result, transferId, errorCode, completedAt) == 1;
    }

    @Override
    public boolean close(
            long transferHoldId,
            TransferHoldStatus result,
            Long actorId,
            String reasonCode,
            LocalDateTime completedAt) {
        requireOneOf(CLOSE_RESULTS, result);
        return repository.close(transferHoldId, result, actorId, reasonCode, completedAt) == 1;
    }

    // 전이표에 없는 결과를 넘긴 건 호출 결함이다
    private static void requireOneOf(Set<TransferHoldStatus> allowed, TransferHoldStatus result) {
        if (!allowed.contains(result)) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
    }
}
