package com.shinhan.corebank.autotransfer.application.service;

import com.shinhan.corebank.autotransfer.api.AutoTransferAccountClosureUseCase;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferAccountClosurePort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferPersistencePort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.exception.AutoTransferErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AutoTransferAccountClosureService implements AutoTransferAccountClosureUseCase {

    private final AutoTransferAccountClosurePort closurePort;
    private final AutoTransferPersistencePort persistencePort;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void terminateLinkedTransfers(Long accountId, String accountNumber, LocalDateTime now) {

        if (accountId == null || accountNumber == null || now == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        List<AutoTransfer> linked = closurePort.findNormalReferencingAccountForUpdate(accountId, accountNumber);

        // 등록 건의 NORMAL 상태와 회차의 PROCESSING 상태는 별개다.
        if (closurePort.hasProcessingExecutionReferencingAccount(accountId, accountNumber)) {
            throw new BusinessException(
                    AutoTransferErrorCode.NOT_IN_NORMAL_STATUS, "처리 중인 자동이체 회차가 있어 계좌를 해지할 수 없습니다.");
        }

        // 먼저 모든 건을 검증한다. 한 건이라도 실행 예정이면 정리하지 않는다.
        for (AutoTransfer transfer : linked) {
            if (!transfer.getNextExecutionDate().isAfter(now.toLocalDate())) {
                throw new BusinessException(AutoTransferErrorCode.CANNOT_TERMINATE_ON_EXECUTION_DATE);
            }
        }

        for (AutoTransfer transfer : linked) {
            transfer.terminate(now);
            persistencePort.save(transfer);
        }
    }
}
