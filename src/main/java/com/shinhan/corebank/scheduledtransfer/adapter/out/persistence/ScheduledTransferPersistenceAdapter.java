package com.shinhan.corebank.scheduledtransfer.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferPersistencePort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import com.shinhan.corebank.scheduledtransfer.domain.exception.ScheduledTransferErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ScheduledTransferPersistenceAdapter implements ScheduledTransferPersistencePort {
    private final ScheduledTransferJpaRepository scheduledTransferJpaRepository;

    @Override
    public ScheduledTransfer save(ScheduledTransfer scheduledTransfer) {
        try {
            ScheduledTransferJpaEntity saved = scheduledTransferJpaRepository.save(ScheduledTransferMapper.toEntity(scheduledTransfer));
            return ScheduledTransferMapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // 사전 existsActiveDuplicate() 확인과 INSERT 사이의 동시성 경쟁 — DB unique 제약(uk_sched_active_dup)이
            // 최종 방어선. 그 위반만 골라 SCD0301로 변환하고, 다른 무결성 위반은 그대로 전파한다.
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.contains("uk_sched_active_dup")) {
                throw new BusinessException(ScheduledTransferErrorCode.DUPLICATE_REGISTRATION);
            }
            throw e;
        }
    }

    @Override
    public Optional<ScheduledTransfer> findById(Long scheduledTransferId) {
        return scheduledTransferJpaRepository.findById(scheduledTransferId)
                .map(ScheduledTransferMapper::toDomain);
    }

    @Override
    public boolean existsActiveDuplicate(Long customerId, Long withdrawalAccountId, String payeeAccountNumber,
                                         Long amount, LocalDate scheduledDate) {
        return scheduledTransferJpaRepository
                .existsByCustomerIdAndWithdrawalAccountIdAndPayeeAccountNumberAndAmountAndScheduledDateAndStatus(
                        customerId, withdrawalAccountId, payeeAccountNumber, amount, scheduledDate,
                        ScheduledTransferStatus.WAITING);
    }
}
