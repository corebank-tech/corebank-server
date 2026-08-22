package com.shinhan.corebank.scheduledtransfer.application;

import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.limit.domain.exception.LmtErrorCode;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferCancelCommand;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferCancelUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferRegisterCommand;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferRegisterUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.out.AccountStatusPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.AuthTokenVerificationPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferOtpVerificationPort;
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
    private final ScheduledTransferOtpVerificationPort scheduledTransferOtpVerificationPort;
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

        // 출금 계좌 소유자 검증
        if (!accountStatusPort.belongsToCustomer(command.withdrawalAccountId(), command.customerId())) {
            throw new BusinessException(ScheduledTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE);
        }

        // 출금계좌 상태 검증
        if (!accountStatusPort.isActiveAccount(command.withdrawalAccountId())) {
            throw new BusinessException(ScheduledTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE);
        }

        // 출금계좌 등록 여부 검증 — 출금계좌로 등록 안 된 계좌는 실제 실행 시점(TransferExecutionService)에서
        // 매번 거부되므로, 등록 시점에 미리 막아야 "등록은 성공했는데 실행이 계속 안 되는" 상황을 방지한다
        if (!accountStatusPort.isWithdrawalRegistered(command.withdrawalAccountId())) {
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
            throw new BusinessException(LmtErrorCode.ONE_TIME_LIMIT_EXCEEDED);
        }

        // 중복 등록 제한
        if (scheduledTransferPersistencePort.existsActiveDuplicate(command.customerId(), command.withdrawalAccountId(),
                command.depositAccountNumber(), command.amount(), command.scheduledDate())) {
            throw new BusinessException(ScheduledTransferErrorCode.DUPLICATE_REGISTRATION);
        }

        // 인증 완료 토큰 — 계좌비밀번호는 P6 실구현 전까지 Mock, OTP는 실제 otp 도메인과 연동한다.
        // OTP는 성공 시 즉시 소비되므로 위 선행 검증을 모두 통과한 뒤 상태 변경 직전에 검증한다(otp_integration_guide.md §9)
        authTokenVerificationPort.verify(command.accountPasswordAuthToken(), command.withdrawalAccountId(), "SCHEDULED_TRANSFER_REGISTER");
        scheduledTransferOtpVerificationPort.verifyRegisterAndConsume(command.otpAuthToken(), command.customerId(),
                command.withdrawalAccountId(), command.depositAccountNumber(), command.amount(), command.scheduledDate());

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
        scheduledTransferOtpVerificationPort.verifyCancelAndConsume(command.otpAuthToken(), command.customerId(), scheduledTransferId);

        scheduledTransfer.cancel(LocalDateTime.now(clock));
        ScheduledTransfer saved = scheduledTransferPersistencePort.save(scheduledTransfer);
        auditLogService.record(saved.getCustomerId(), null, AuditEventType.SCHEDULED_TRANSFER_INFO_CHANGE,
                command.requestIp(), true, Map.of("scheduledTransferId", saved.getScheduledTransferId(), "action", "cancel"));

        return saved;
    }

    // 존재 여부를 숨기기 위해 findById 실패와 동일한 NOT_FOUND로 응답한다 (api_conventions.md §8-3)
    private void requireOwned(ScheduledTransfer scheduledTransfer, Long customerId) {
        if (!scheduledTransfer.getCustomerId().equals(customerId)) {
            throw new BusinessException(ScheduledTransferErrorCode.NOT_FOUND);
        }
    }
}
