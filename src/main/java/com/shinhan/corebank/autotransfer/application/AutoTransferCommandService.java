package com.shinhan.corebank.autotransfer.application;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferRegisterCommand;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferRegisterUseCase;
import com.shinhan.corebank.autotransfer.application.port.out.AccountStatusPort;
import com.shinhan.corebank.autotransfer.application.port.out.AuthTokenVerificationPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferPersistencePort;
import com.shinhan.corebank.autotransfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class AutoTransferCommandService implements AutoTransferRegisterUseCase {
    private final AutoTransferPersistencePort autoTransferPersistencePort;
    private final AuthTokenVerificationPort authTokenVerificationPort;
    private final AccountStatusPort accountStatusPort;
    private final TransferLimitPort transferLimitPort;

    @Override
    public AutoTransfer register(AutoTransferRegisterCommand command) {

        // 인증 완료 토큰
        authTokenVerificationPort.verify(command.authToken(),command.withdrawalAccountId(),"AUTO_TRANSFER_REGISTER");

        // 출금계좌 상태 검증
        if (!accountStatusPort.isActiveAccount(command.withdrawalAccountId())) {
            throw new BusinessException(AutoTransferErrorCode.WITHDRAWAL_ACCOUNT_SUSPENDED);
        }

        // 중복 등록 제한
        if (autoTransferPersistencePort.existsActiveDuplicate(command.withdrawalAccountId(), command.depositAccountNumber(),
                command.transferDay())) {
            throw new BusinessException(AutoTransferErrorCode.DUPLICATE_REGISTRATION);
        }

        AutoTransfer autoTransfer = AutoTransfer.register(command.customerId(), command.withdrawalAccountId(),command.depositAccountNumber(), command.payeeName(),
                command.amount(), command.cycleMonths(), command.transferDay(), command.startDate(), command.endDate(),
                command.myPassbookMemo(), command.recipientPassbookMemo(), LocalDateTime.now());

        return autoTransferPersistencePort.save(autoTransfer);
    }
}
