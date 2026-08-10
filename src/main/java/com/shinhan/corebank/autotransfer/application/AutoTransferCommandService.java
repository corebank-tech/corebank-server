package com.shinhan.corebank.autotransfer.application;

import com.shinhan.corebank.autotransfer.application.port.in.*;
import com.shinhan.corebank.autotransfer.application.port.out.AccountStatusPort;
import com.shinhan.corebank.autotransfer.application.port.out.AuthTokenVerificationPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferPersistencePort;
import com.shinhan.corebank.autotransfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferErrorCode;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.account.domain.AccountType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class AutoTransferCommandService implements AutoTransferRegisterUseCase, AutoTransferChangeUseCase, AutoTransferCancelUseCase {
    private final AutoTransferPersistencePort autoTransferPersistencePort;
    private final AuthTokenVerificationPort authTokenVerificationPort;
    private final AccountStatusPort accountStatusPort;
    private final TransferLimitPort transferLimitPort;
    private final AuditLogService auditLogService;


    @Override
    public AutoTransfer register(AutoTransferRegisterCommand command) {

        // 인증 완료 토큰
        authTokenVerificationPort.verify(command.accountPasswordAuthToken(),command.withdrawalAccountId(),"AUTO_TRANSFER_REGISTER");

        // 출금 계좌 소유자 검증
        if(!accountStatusPort.belongsToCustomer(command.withdrawalAccountId(), command.customerId())) {
            throw new BusinessException(AutoTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE);
        }

        // 출금계좌 상태 검증
        if (!accountStatusPort.isActiveAccount(command.withdrawalAccountId())) {
            throw new BusinessException(AutoTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE);
        }

        // 입금계좌 실존 여부·유형 검증
        AccountType depositAccountType = accountStatusPort.findAccountTypeByNumber(command.depositAccountNumber())
                .orElseThrow(() -> new BusinessException(AutoTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));
        if (depositAccountType != AccountType.DEMAND_DEPOSIT) {
            throw new BusinessException(AutoTransferErrorCode.UNSUPPORTED_DEPOSIT_ACCOUNT_TYPE);
        }

        // 1회 이체한도 검증
        long oneTimeLimit = transferLimitPort.findOneTimeLimit(command.customerId());
        if (command.amount() > oneTimeLimit) {
            throw new BusinessException(AutoTransferErrorCode.ONE_TIME_LIMIT_EXCEEDED);
        }

        // 중복 등록 제한
        if (autoTransferPersistencePort.existsActiveDuplicate(command.withdrawalAccountId(), command.depositAccountNumber(),
                command.transferDay())) {
            throw new BusinessException(AutoTransferErrorCode.DUPLICATE_REGISTRATION);
        }

        AutoTransfer autoTransfer = AutoTransfer.register(command.customerId(), command.withdrawalAccountId(),command.depositAccountNumber(), command.payeeName(),
                command.amount(), command.cycleMonths(), command.transferDay(), command.startDate(), command.endDate(),
                command.myPassbookMemo(), command.recipientPassbookMemo(), LocalDateTime.now());

        AutoTransfer saved = autoTransferPersistencePort.save(autoTransfer);
        auditLogService.record(saved.getCustomerId(), null, AuditEventType.AUTO_TRANSFER_INFO_CHANGE,
                command.requestIp(), true, Map.of("autoTransferId", saved.getAutoTransferId(), "action", "register"));

        return saved;
    }

    @Override
    public AutoTransfer change(Long autoTransferId, AutoTransferChangeCommand command) {
        AutoTransfer autoTransfer = autoTransferPersistencePort.findById(autoTransferId).orElseThrow(() ->  new BusinessException(AutoTransferErrorCode.NOT_FOUND));
        if (!autoTransfer.getCustomerId().equals(command.customerId())) {
            throw new BusinessException(AutoTransferErrorCode.NOT_FOUND);
        }
        authTokenVerificationPort.verify(command.accountPasswordAuthToken(),autoTransfer.getWithdrawalAccountId(),"AUTO_TRANSFER_CHANGE");
        // 이체한도 재검증
        if (command.amount() != null) {
            long oneTimeLimit = transferLimitPort.findOneTimeLimit(autoTransfer.getCustomerId());
            if(command.amount() > oneTimeLimit) {
                throw new BusinessException(AutoTransferErrorCode.ONE_TIME_LIMIT_EXCEEDED);
            }
        }
        autoTransfer.change(command.amount(), command.cycleMonths(), command.endDate(), command.myPassbookMemo(), command.recipientPassbookMemo());
        AutoTransfer saved = autoTransferPersistencePort.save(autoTransfer);
        auditLogService.record(saved.getCustomerId(),null, AuditEventType.AUTO_TRANSFER_INFO_CHANGE, command.requestIp(), true, Map.of("autoTransferId", saved.getAutoTransferId(),
                "amount", saved.getAmount(), "cycleMonths", saved.getCycleMonths(), "endDate", saved.getEndDate().toString()));

        return saved;
    }

    @Override
    public void cancel(Long autoTransferId, AutoTransferCancelCommand command) {
        AutoTransfer autoTransfer = autoTransferPersistencePort.findById(autoTransferId).orElseThrow(()->
                new BusinessException(AutoTransferErrorCode.NOT_FOUND));
        if (!autoTransfer.getCustomerId().equals(command.customerId())) {
            throw new BusinessException(AutoTransferErrorCode.NOT_FOUND);
        }
        authTokenVerificationPort.verify(command.accountPasswordAuthToken(), autoTransfer.getWithdrawalAccountId(),"AUTO_TRANSFER_CANCEL");
        autoTransfer.terminate(LocalDateTime.now());
        AutoTransfer saved = autoTransferPersistencePort.save(autoTransfer);
        auditLogService.record(saved.getCustomerId(), null, AuditEventType.AUTO_TRANSFER_INFO_CHANGE,
                command.requestIp(), true, Map.of("autoTransferId", saved.getAutoTransferId(),"action","cancel"));
    }
}
