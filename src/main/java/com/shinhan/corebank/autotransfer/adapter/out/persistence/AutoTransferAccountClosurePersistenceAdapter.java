package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferAccountClosurePort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.autotransfer.domain.exception.AutoTransferErrorCode;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AutoTransferAccountClosurePersistenceAdapter implements AutoTransferAccountClosurePort {

    private final AutoTransferJpaRepository autoTransferRepository;
    private final AutoTransferExecutionJpaRepository executionRepository;

    @Override
    public List<AutoTransfer> findNormalReferencingAccountForUpdate(Long accountId, String accountNumber) {

        return autoTransferRepository.findLinkedForUpdate(accountId, accountNumber, AutoTransferStatus.NORMAL).stream()
                .map(AutoTransferMapper::toDomain)
                .toList();
    }

    @Override
    public boolean hasProcessingExecutionForAutoTransferIds(List<Long> autoTransferIds) {

        if (autoTransferIds.isEmpty()) {
            return false;
        }

        try {
            return !executionRepository
                    .findProcessingForShareNowait(autoTransferIds, ProcessResultStatus.PROCESSING)
                    .isEmpty();
        } catch (PessimisticLockingFailureException e) {
            throw new BusinessException(
                    AutoTransferErrorCode.NOT_IN_NORMAL_STATUS, "처리 중인 자동이체 회차가 있어 계좌를 해지할 수 없습니다.");
        }
    }
}
