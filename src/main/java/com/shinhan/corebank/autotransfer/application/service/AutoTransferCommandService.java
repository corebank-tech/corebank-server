package com.shinhan.corebank.autotransfer.application.service;

import com.shinhan.corebank.autotransfer.application.port.in.*;
import com.shinhan.corebank.autotransfer.application.port.out.AccountStatusPort;
import com.shinhan.corebank.autotransfer.application.port.out.AuthTokenVerificationPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferOtpVerificationPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferPersistencePort;
import com.shinhan.corebank.autotransfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferErrorCode;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.limit.domain.exception.LmtErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class AutoTransferCommandService implements AutoTransferRegisterUseCase, AutoTransferChangeUseCase, AutoTransferCancelUseCase {
    private final AutoTransferPersistencePort autoTransferPersistencePort;
    private final AuthTokenVerificationPort authTokenVerificationPort;
    private final AutoTransferOtpVerificationPort autoTransferOtpVerificationPort;
    private final AccountStatusPort accountStatusPort;
    private final TransferLimitPort transferLimitPort;
    private final AuditLogService auditLogService;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock;

    @Override
    public AutoTransfer register(AutoTransferRegisterCommand command) {

        // 출금 계좌 소유자 검증
        if (!accountStatusPort.belongsToCustomer(command.withdrawalAccountId(), command.customerId())) {
            throw new BusinessException(AutoTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE);
        }

        // 출금계좌 상태 검증
        if (!accountStatusPort.isActiveAccount(command.withdrawalAccountId())) {
            throw new BusinessException(AutoTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE);
        }

        // 출금계좌 등록 여부 검증 — 출금계좌로 등록 안 된 계좌는 실제 실행 시점(TransferExecutionService)에서
        // 매번 거부되므로, 등록 시점에 미리 막아야 "등록은 성공했는데 실행이 계속 안 되는" 상황을 방지한다
        if (!accountStatusPort.isWithdrawalRegistered(command.withdrawalAccountId())) {
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
            throw new BusinessException(LmtErrorCode.ONE_TIME_LIMIT_EXCEEDED);
        }

        // 중복 등록 제한
        if (autoTransferPersistencePort.existsActiveDuplicate(command.withdrawalAccountId(), command.depositAccountNumber(),
                command.transferDay())) {
            throw new BusinessException(AutoTransferErrorCode.DUPLICATE_REGISTRATION);
        }

        // 인증 완료 토큰 — OTP는 성공 시 즉시 소비되므로 위 선행 검증을 모두 통과한 뒤 상태 변경 직전에 검증한다
        // (otp_integration_guide.md §9)
        authTokenVerificationPort.verify(command.accountPasswordAuthToken(), command.withdrawalAccountId(), "AUTO_TRANSFER_REGISTER");
        autoTransferOtpVerificationPort.verifyRegisterAndConsume(command.otpAuthToken(), command.customerId(),
                command.withdrawalAccountId(), command.depositAccountNumber(), command.amount(), command.cycleMonths(),
                command.transferDay(), command.startDate(), command.endDate());

        AutoTransfer autoTransfer = AutoTransfer.register(command.customerId(), command.withdrawalAccountId(), command.depositAccountNumber(), command.payeeName(),
                command.amount(), command.cycleMonths(), command.transferDay(), command.startDate(), command.endDate(),
                command.myPassbookMemo(), command.recipientPassbookMemo(), LocalDateTime.now(clock.withZone(SEOUL)));

        AutoTransfer saved = autoTransferPersistencePort.save(autoTransfer);
        auditLogService.record(saved.getCustomerId(), null, AuditEventType.AUTO_TRANSFER_INFO_CHANGE,
                command.requestIp(), true, Map.of("autoTransferId", saved.getAutoTransferId(), "action", "register"));

        return saved;
    }

    @Override
    public AutoTransfer change(Long autoTransferId, AutoTransferChangeCommand command) {
        AutoTransfer autoTransfer = autoTransferPersistencePort.findById(autoTransferId).orElseThrow(() -> new BusinessException(AutoTransferErrorCode.NOT_FOUND));
        requireOwned(autoTransfer, command.customerId());
        // 상태 검증을 한도 검증보다 먼저 해야 한다
        // — 정상 상태가 아닌 건을 한도 초과 금액으로 바꾸면 진짜 원인(AUT0302)이 아니라 LMT0002으로 잘못 응답하게 된다
        if (!autoTransfer.getStatus().isModifiable()) {
            throw new BusinessException(AutoTransferErrorCode.NOT_IN_NORMAL_STATUS);
        }
        // 이체한도 재검증
        if (command.amount() != null) {
            long oneTimeLimit = transferLimitPort.findOneTimeLimit(autoTransfer.getCustomerId());
            if (command.amount() > oneTimeLimit) {
                throw new BusinessException(LmtErrorCode.ONE_TIME_LIMIT_EXCEEDED);
            }
        }
        // 인증 완료 토큰 — OTP는 성공 시 즉시 소비되므로 위 무관한 검증을 모두 통과한 뒤
        // 상태 변경 직전에 검증한다(otp_integration_guide.md §9)
        authTokenVerificationPort.verify(command.accountPasswordAuthToken(), autoTransfer.getWithdrawalAccountId(), "AUTO_TRANSFER_CHANGE");
        autoTransferOtpVerificationPort.verifyChangeAndConsume(command.otpAuthToken(), command.customerId(), autoTransferId,
                command.amount(), command.cycleMonths(), command.endDate());
        autoTransfer.change(command.amount(), command.cycleMonths(), command.endDate(), command.myPassbookMemo(), command.recipientPassbookMemo());
        AutoTransfer saved = autoTransferPersistencePort.save(autoTransfer);
        auditLogService.record(saved.getCustomerId(), null, AuditEventType.AUTO_TRANSFER_INFO_CHANGE, command.requestIp(), true, Map.of("autoTransferId", saved.getAutoTransferId(),
                "amount", saved.getAmount(), "cycleMonths", saved.getCycleMonths(), "endDate", saved.getEndDate().toString()));

        return saved;
    }

    @Override
    public void cancel(Long autoTransferId, AutoTransferCancelCommand command) {
        AutoTransfer autoTransfer = autoTransferPersistencePort.findById(autoTransferId).orElseThrow(() ->
                new BusinessException(AutoTransferErrorCode.NOT_FOUND));
        requireOwned(autoTransfer, command.customerId());

        LocalDateTime now = LocalDateTime.now(clock.withZone(SEOUL));

        // terminate()가 내부에서도 같은 검증을 하지만, OTP는 성공 시 즉시 소비되므로 change()와
        // 동일한 이유로 실패할 수 있는 이 검증들을 OTP 소비 앞으로 당긴다(otp_integration_guide.md §9).
        if (!autoTransfer.getStatus().isModifiable()) {
            throw new BusinessException(AutoTransferErrorCode.NOT_IN_NORMAL_STATUS);
        }
        if (autoTransfer.getNextExecutionDate().equals(now.toLocalDate())) {
            throw new BusinessException(AutoTransferErrorCode.CANNOT_TERMINATE_ON_EXECUTION_DATE);
        }

        authTokenVerificationPort.verify(command.accountPasswordAuthToken(), autoTransfer.getWithdrawalAccountId(), "AUTO_TRANSFER_CANCEL");
        autoTransferOtpVerificationPort.verifyCancelAndConsume(command.otpAuthToken(), command.customerId(), autoTransferId);
        autoTransfer.terminate(now);
        AutoTransfer saved = autoTransferPersistencePort.save(autoTransfer);
        auditLogService.record(saved.getCustomerId(), null, AuditEventType.AUTO_TRANSFER_INFO_CHANGE,
                command.requestIp(), true, Map.of("autoTransferId", saved.getAutoTransferId(), "action", "cancel"));
    }

    // change()/cancel() 둘 다 findById() 이후 소유자 확인이 필요하다
    // — 존재 자체를 숨기기 위해 별도 코드(FORBIDDEN) 대신 findById 실패와 동일한 NOT_FOUND를 던진다
    private void requireOwned(AutoTransfer autoTransfer, Long customerId) {
        if (!autoTransfer.getCustomerId().equals(customerId)) {
            throw new BusinessException(AutoTransferErrorCode.NOT_FOUND);
        }
    }
}
