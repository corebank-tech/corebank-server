package com.shinhan.corebank.scheduledtransfer.application;

import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferRegisterCommand;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferRegisterUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.out.AccountStatusPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.AuthTokenVerificationPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferPersistencePort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.exception.ScheduledTransferErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduledTransferCommandService implements ScheduledTransferRegisterUseCase {

    // 1차는 당행 전용 상수. 별도 은행 테이블 없음(scheduled_transfer.payee_bank_code 스키마 주석 참고)
    private static final String PAYEE_BANK_CODE = "088";

    private final ScheduledTransferPersistencePort scheduledTransferPersistencePort;
    private final AuthTokenVerificationPort authTokenVerificationPort;
    private final AccountStatusPort accountStatusPort;
    private final TransferLimitPort transferLimitPort;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    public ScheduledTransfer register(ScheduledTransferRegisterCommand command) {

        // 인증 완료 토큰
        authTokenVerificationPort.verify(command.accountPasswordAuthToken(), command.withdrawalAccountId(), "SCHEDULED_TRANSFER_REGISTER");

        // 출금 계좌 소유자 검증
        if (!accountStatusPort.belongsToCustomer(command.withdrawalAccountId(), command.customerId())) {
            throw new BusinessException(ScheduledTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE);
        }

        // 출금계좌 상태 검증
        if (!accountStatusPort.isActiveAccount(command.withdrawalAccountId())) {
            throw new BusinessException(ScheduledTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE);
        }

        // 입금계좌 실존 여부·유형 검증
        AccountType payeeAccountType = accountStatusPort.findAccountTypeByNumber(command.payeeAccountNumber())
                .orElseThrow(() -> new BusinessException(ScheduledTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));
        if (payeeAccountType != AccountType.DEMAND_DEPOSIT) {
            throw new BusinessException(ScheduledTransferErrorCode.UNSUPPORTED_DEPOSIT_ACCOUNT_TYPE);
        }

        // 1회 이체한도 검증
        long oneTimeLimit = transferLimitPort.findOneTimeLimit(command.customerId());
        if (command.amount() > oneTimeLimit) {
            throw new BusinessException(ScheduledTransferErrorCode.ONE_TIME_LIMIT_EXCEEDED);
        }

        // 중복 등록 제한
        if (scheduledTransferPersistencePort.existsActiveDuplicate(command.customerId(), command.withdrawalAccountId(),
                command.payeeAccountNumber(), command.amount(), command.scheduledDate())) {
            throw new BusinessException(ScheduledTransferErrorCode.DUPLICATE_REGISTRATION);
        }

        ScheduledTransfer scheduledTransfer = ScheduledTransfer.register(command.customerId(), command.withdrawalAccountId(),
                PAYEE_BANK_CODE, command.payeeAccountNumber(), command.payeeName(), command.amount(), command.scheduledDate(),
                command.myPassbookMemo(), command.recipientPassbookMemo(), LocalDateTime.now(clock));

        ScheduledTransfer saved = scheduledTransferPersistencePort.save(scheduledTransfer);
        auditLogService.record(saved.getCustomerId(), null, AuditEventType.SCHEDULED_TRANSFER_INFO_CHANGE,
                command.requestIp(), true, Map.of("scheduledTransferId", saved.getScheduledTransferId(), "action", "register"));

        return saved;
    }
}
