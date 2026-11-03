package com.shinhan.corebank.scheduledtransfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.scheduledtransfer.api.ScheduledTransferAccountClosureUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferAccountClosurePort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferPersistencePort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import com.shinhan.corebank.scheduledtransfer.domain.exception.ScheduledTransferErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScheduledTransferAccountClosureService implements ScheduledTransferAccountClosureUseCase {

    private final ScheduledTransferAccountClosurePort closurePort;
    private final ScheduledTransferPersistencePort persistencePort;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelLinkedTransfers(Long accountId, String accountNumber, LocalDateTime now) {

        if (accountId == null || accountNumber == null || now == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        List<ScheduledTransfer> linked = closurePort.findPendingReferencingAccountForUpdate(accountId, accountNumber);

        // 전체를 먼저 검증한다.
        for (ScheduledTransfer transfer : linked) {
            if (transfer.getStatus() == ScheduledTransferStatus.PROCESSING) {
                throw new BusinessException(
                        ScheduledTransferErrorCode.NOT_IN_WAITING_STATUS, "처리 중인 예약이체가 있어 계좌를 해지할 수 없습니다.");
            }

            if (!transfer.getScheduledDate().isAfter(now.toLocalDate())) {
                throw new BusinessException(ScheduledTransferErrorCode.CANNOT_CANCEL_ON_EXECUTION_DATE);
            }
        }

        for (ScheduledTransfer transfer : linked) {
            transfer.cancel(now);
            persistencePort.save(transfer);
        }
    }
}
