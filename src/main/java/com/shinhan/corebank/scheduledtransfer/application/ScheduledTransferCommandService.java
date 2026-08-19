package com.shinhan.corebank.scheduledtransfer.application;

import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferCancelCommand;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferCancelUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferRegisterCommand;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferRegisterUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.out.AccountStatusPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.AuthTokenVerificationPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferPersistencePort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import com.shinhan.corebank.scheduledtransfer.domain.exception.ScheduledTransferErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduledTransferCommandService implements ScheduledTransferRegisterUseCase, ScheduledTransferCancelUseCase {

    // 1차는 당행 전용 상수. 별도 은행 테이블 없음(scheduled_transfer.payee_bank_code 스키마 주석 참고)
    private static final String PAYEE_BANK_CODE = "088";
    private static final int MAX_SCHEDULED_DAYS = 365;

    private final ScheduledTransferPersistencePort scheduledTransferPersistencePort;
    private final AuthTokenVerificationPort authTokenVerificationPort;
    private final AccountStatusPort accountStatusPort;
    private final TransferLimitPort transferLimitPort;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    public ScheduledTransfer register(ScheduledTransferRegisterCommand command) {

        // 예정일자 범위 검증 — 외부 조회 없이 입력값만으로 판단 가능하므로 인증(1회성 토큰 소비)보다 먼저 수행한다
        LocalDate today = LocalDate.now(clock);
        if (!command.scheduledDate().isAfter(today) ||
                command.scheduledDate().isAfter(today.plusDays(MAX_SCHEDULED_DAYS))) {
            throw new BusinessException(ScheduledTransferErrorCode.INVALID_SCHEDULED_DATE);
        }

        // 인증 완료 토큰
        authTokenVerificationPort.verify(command.accountPasswordAuthToken(), command.withdrawalAccountId(), "SCHEDULED_TRANSFER_REGISTER");
        authTokenVerificationPort.verify(command.otpAuthToken(), command.withdrawalAccountId(), "SCHEDULED_TRANSFER_REGISTER_OTP");
        // 출금 계좌 소유자 검증
        if (!accountStatusPort.belongsToCustomer(command.withdrawalAccountId(), command.customerId())) {
            throw new BusinessException(ScheduledTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE);
        }

        // 출금계좌 상태 검증
        if (!accountStatusPort.isActiveAccount(command.withdrawalAccountId())) {
            throw new BusinessException(ScheduledTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE);
        }

        // 입금계좌 실존 여부·유형 검증
        AccountType payeeAccountType = accountStatusPort.findAccountTypeByNumber(command.depositAccountNumber())
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
                command.depositAccountNumber(), command.amount(), command.scheduledDate())) {
            throw new BusinessException(ScheduledTransferErrorCode.DUPLICATE_REGISTRATION);
        }

        ScheduledTransfer scheduledTransfer = ScheduledTransfer.register(command.customerId(), command.withdrawalAccountId(),
                PAYEE_BANK_CODE, command.depositAccountNumber(), command.payeeName(), command.amount(), command.scheduledDate(),
                command.myPassbookMemo(), command.recipientPassbookMemo(), LocalDateTime.now(clock));

        ScheduledTransfer saved = scheduledTransferPersistencePort.save(scheduledTransfer);
        auditLogService.record(saved.getCustomerId(), null, AuditEventType.SCHEDULED_TRANSFER_INFO_CHANGE,
                command.requestIp(), true, Map.of("scheduledTransferId", saved.getScheduledTransferId(), "action", "register"));

        return saved;
    }

    @Override
    public ScheduledTransfer cancel(Long scheduledTransferId, ScheduledTransferCancelCommand command) {
        ScheduledTransfer scheduledTransfer = scheduledTransferPersistencePort.findById(scheduledTransferId)
                .orElseThrow(() -> new BusinessException(ScheduledTransferErrorCode.NOT_FOUND));
        requireOwned(scheduledTransfer, command.customerId());

        // 이미 취소된 건 재요청은 멱등 성공 처리
        if (scheduledTransfer.getStatus() == ScheduledTransferStatus.CANCELED) {
            return scheduledTransfer;
        }
        if (!scheduledTransfer.getStatus().isCancelable()) {
            throw new BusinessException(ScheduledTransferErrorCode.NOT_IN_WAITING_STATUS);
        }

        // 예정일 당일 여부
        LocalDate today = LocalDate.now(clock);
        if (!scheduledTransfer.getScheduledDate().isAfter(today)) {
            throw new BusinessException(ScheduledTransferErrorCode.CANNOT_CANCEL_ON_EXECUTION_DATE);
        }

        authTokenVerificationPort.verify(command.accountPasswordAuthToken(), scheduledTransfer.getWithdrawalAccountId(), "SCHEDULED_TRANSFER_CANCEL");
        authTokenVerificationPort.verify(command.otpAuthToken(), scheduledTransfer.getWithdrawalAccountId(), "SCHEDULED_TRANSFER_CANCEL_OTP");

        scheduledTransfer.cancel(LocalDateTime.now(clock));
        ScheduledTransfer saved = scheduledTransferPersistencePort.save(scheduledTransfer);
        auditLogService.record(saved.getCustomerId(), null, AuditEventType.SCHEDULED_TRANSFER_INFO_CHANGE,
                command.requestIp(), true, Map.of("scheduledTransferId", saved.getScheduledTransferId(), "action", "cancel"));

        return saved;
    }

    // 예약이체 취소 403/404를 구분
    private void requireOwned(ScheduledTransfer scheduledTransfer, Long customerId) {
        if (!scheduledTransfer.getCustomerId().equals(customerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}
