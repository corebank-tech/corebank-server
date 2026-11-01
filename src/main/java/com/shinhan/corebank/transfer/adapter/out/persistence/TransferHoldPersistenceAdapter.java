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
    public boolean markExecuting(long transferHoldId, Long actorId, LocalDateTime executingAt) {
        return repository.markExecuting(transferHoldId, actorId, executingAt) == 1;
    }

    @Override
    public boolean finishExecution(
            long transferHoldId, TransferHoldStatus result, Long transferId, LocalDateTime completedAt) {
        requireOneOf(EXECUTION_RESULTS, result);
        return repository.finishExecution(transferHoldId, result, transferId, completedAt) == 1;
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
