package com.shinhan.corebank.autotransfer.application.service;

import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.autotransfer.application.port.in.*;
import com.shinhan.corebank.autotransfer.application.port.out.AccountStatusPort;
import com.shinhan.corebank.autotransfer.application.port.out.AuthTokenVerificationPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferOtpVerificationPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferPersistencePort;
import com.shinhan.corebank.autotransfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferErrorCode;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.limit.domain.exception.LmtErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AutoTransferCommandService
        implements AutoTransferRegisterUseCase, AutoTransferChangeUseCase, AutoTransferCancelUseCase {
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

        // 입금계좌 실존 여부·유형 검증. 정기예금(TIME_DEPOSIT)은 만기까지 목돈을 묶어두는 상품이라 이체로 추가 입금할 수 없다.
        // 정기적금(INSTALLMENT_SAVINGS)은 매달 나눠 넣는 게 상품 목적이라 허용한다(REQ-PRDT-012, REQ-TRSF-030)
        AccountType depositAccountType = accountStatusPort
                .findAccountTypeByNumber(command.depositAccountNumber())
                .orElseThrow(() -> new BusinessException(AutoTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));
        if (depositAccountType == AccountType.TIME_DEPOSIT) {
            throw new BusinessException(AutoTransferErrorCode.UNSUPPORTED_DEPOSIT_ACCOUNT_TYPE);
        }

        // 1회 이체한도 검증
        long oneTimeLimit = transferLimitPort.findOneTimeLimit(command.customerId());
        if (command.amount() > oneTimeLimit) {
            throw new BusinessException(LmtErrorCode.ONE_TIME_LIMIT_EXCEEDED);
        }

        // 중복 등록 제한
        if (autoTransferPersistencePort.existsActiveDuplicate(
                command.withdrawalAccountId(), command.depositAccountNumber(), command.transferDay())) {
            throw new BusinessException(AutoTransferErrorCode.DUPLICATE_REGISTRATION);
        }

        // 인증 완료 토큰 — OTP는 성공 시 즉시 소비되므로 위 선행 검증을 모두 통과한 뒤 상태 변경 직전에 검증한다
        // (otp_integration_guide.md §9)
        authTokenVerificationPort.verify(
                command.accountPasswordAuthToken(), command.withdrawalAccountId(), "AUTO_TRANSFER_REGISTER");
        autoTransferOtpVerificationPort.verifyRegisterAndConsume(
                command.otpAuthToken(),
                command.customerId(),
                command.withdrawalAccountId(),
                command.depositAccountNumber(),
                command.amount(),
                command.cycleMonths(),
                command.transferDay(),
                command.startDate(),
                command.endDate());

        AutoTransfer autoTransfer = AutoTransfer.register(
                command.customerId(),
                command.withdrawalAccountId(),
                command.depositAccountNumber(),
                command.payeeName(),
                command.amount(),
                command.cycleMonths(),
                command.transferDay(),
                command.startDate(),
                command.endDate(),
                command.myPassbookMemo(),
                command.recipientPassbookMemo(),
                LocalDateTime.now(clock.withZone(SEOUL)));

        AutoTransfer saved = autoTransferPersistencePort.save(autoTransfer);
        auditLogService.record(
                saved.getCustomerId(),
                null,
                AuditEventType.AUTO_TRANSFER_INFO_CHANGE,
                command.requestIp(),
                true,
                Map.of("autoTransferId", saved.getAutoTransferId(), "action", "register"));

        return saved;
    }

    @Override
    public AutoTransfer change(Long autoTransferId, AutoTransferChangeCommand command) {
        AutoTransfer autoTransfer = autoTransferPersistencePort
                .findById(autoTransferId)
                .orElseThrow(() -> new BusinessException(AutoTransferErrorCode.NOT_FOUND));
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
        authTokenVerificationPort.verify(
                command.accountPasswordAuthToken(), autoTransfer.getWithdrawalAccountId(), "AUTO_TRANSFER_CHANGE");
        autoTransferOtpVerificationPort.verifyChangeAndConsume(
                command.otpAuthToken(),
                command.customerId(),
                autoTransferId,
                command.amount(),
                command.cycleMonths(),
                command.endDate());
        autoTransfer.change(
                command.amount(),
                command.cycleMonths(),
                command.endDate(),
                command.myPassbookMemo(),
                command.recipientPassbookMemo());
        AutoTransfer saved = autoTransferPersistencePort.save(autoTransfer);
        auditLogService.record(
                saved.getCustomerId(),
                null,
                AuditEventType.AUTO_TRANSFER_INFO_CHANGE,
                command.requestIp(),
                true,
                Map.of(
                        "autoTransferId",
                        saved.getAutoTransferId(),
                        "amount",
                        saved.getAmount(),
                        "cycleMonths",
                        saved.getCycleMonths(),
                        "endDate",
                        saved.getEndDate().toString()));

        return saved;
    }

    @Override
    public List<AutoTransferCancelResult> cancel(AutoTransferCancelCommand command) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(SEOUL));

        // 건별 사전검증 — terminate()가 내부에서도 같은 검증을 하지만, OTP는 성공 시 즉시 소비되므로
        // 실패할 수 있는 검증을 OTP 소비 앞으로 당긴다(otp_integration_guide.md §9).
        // 여기서 걸러진 건은 예외가 아니라 건별 실패 결과로 남고, 나머지 건의 해지를 막지 않는다.
        Map<Long, AutoTransferCancelResult> results = new HashMap<>();
        List<AutoTransfer> owned = new ArrayList<>();
        List<AutoTransfer> cancelable = new ArrayList<>();
        for (Long autoTransferId : command.autoTransferIds()) {
            Optional<AutoTransfer> found = autoTransferPersistencePort.findById(autoTransferId);
            // 미존재와 타인 소유를 구분하지 않는다 — 존재 자체를 숨기기 위한 것이다(api_conventions.md §8-3)
            if (found.isEmpty() || !found.get().getCustomerId().equals(command.customerId())) {
                results.put(
                        autoTransferId,
                        AutoTransferCancelResult.failure(autoTransferId, AutoTransferErrorCode.NOT_FOUND));
                continue;
            }
            AutoTransfer autoTransfer = found.get();
            owned.add(autoTransfer);
            // 이미 해지된 건 재요청은 멱등 성공 처리(예약이체의 CANCELED 재요청과 같은 규칙).
            // 기간 만료로 시스템이 종료시킨 EXPIRED는 고객이 해지한 적이 없으므로 아래에서 AUT0302로 남긴다.
            if (autoTransfer.getStatus() == AutoTransferStatus.TERMINATED) {
                results.put(autoTransferId, AutoTransferCancelResult.success(autoTransfer));
                continue;
            }
            if (!autoTransfer.getStatus().isModifiable()) {
                results.put(
                        autoTransferId,
                        AutoTransferCancelResult.failure(autoTransferId, AutoTransferErrorCode.NOT_IN_NORMAL_STATUS));
                continue;
            }
            if (autoTransfer.getNextExecutionDate().equals(now.toLocalDate())) {
                results.put(
                        autoTransferId,
                        AutoTransferCancelResult.failure(
                                autoTransferId, AutoTransferErrorCode.CANNOT_TERMINATE_ON_EXECUTION_DATE));
                continue;
            }
            cancelable.add(autoTransfer);
        }

        // 출금계좌 혼합 여부는 상태와 무관한 요청 단위 계약이므로 소유가 확인된 전체를 기준으로 먼저 막는다.
        // cancelable만 검사하면 "이미 해지된 건이 다른 계좌"인 조합이 계약을 빠져나간다.
        requireSingleWithdrawalAccount(owned);

        // 실제로 해지할 건이 하나도 없으면 인증 토큰을 소비하지 않는다 — 고객이 OTP를 다시 발급받지 않아도 되도록
        if (cancelable.isEmpty()) {
            return orderedResults(command.autoTransferIds(), results);
        }

        // 위에서 단일 계좌임을 확인했으므로 어느 건의 출금계좌를 써도 같다
        authTokenVerificationPort.verify(
                command.accountPasswordAuthToken(),
                cancelable.getFirst().getWithdrawalAccountId(),
                "AUTO_TRANSFER_CANCEL");
        // OTP 토큰에는 요청한 id 조합 전체가 묶여 있다 — 해지 가능한 건만 추려서 넘기면
        // 발급 시점 거래정보와 어긋나 OTP0102가 난다
        autoTransferOtpVerificationPort.verifyCancelAndConsume(
                command.otpAuthToken(), command.customerId(), command.autoTransferIds());

        for (AutoTransfer autoTransfer : cancelable) {
            autoTransfer.terminate(now);
            AutoTransfer saved = autoTransferPersistencePort.save(autoTransfer);
            auditLogService.record(
                    saved.getCustomerId(),
                    null,
                    AuditEventType.AUTO_TRANSFER_INFO_CHANGE,
                    command.requestIp(),
                    true,
                    Map.of("autoTransferId", saved.getAutoTransferId(), "action", "cancel"));
            results.put(saved.getAutoTransferId(), AutoTransferCancelResult.success(saved));
        }

        return orderedResults(command.autoTransferIds(), results);
    }

    // accountPasswordAuthToken은 계좌 하나에 묶여 발급된다(api_conventions.md §6-3).
    // 출금계좌가 섞인 조합은 토큰 하나로 인증할 수 없으므로 건별 실패가 아니라 요청 자체를 거부한다.
    private void requireSingleWithdrawalAccount(List<AutoTransfer> owned) {
        long distinctWithdrawalAccounts = owned.stream()
                .map(AutoTransfer::getWithdrawalAccountId)
                .distinct()
                .count();
        if (distinctWithdrawalAccounts > 1) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "다건 해지는 같은 출금계좌의 자동이체만 함께 요청할 수 있습니다.");
        }
    }

    // 요청한 id 순서 그대로 건별 결과를 정렬해 돌려준다 — 화면이 선택 목록과 결과를 짝지을 수 있도록
    private List<AutoTransferCancelResult> orderedResults(
            List<Long> autoTransferIds, Map<Long, AutoTransferCancelResult> results) {
        return autoTransferIds.stream()
                .map(autoTransferId -> Objects.requireNonNull(
                        results.get(autoTransferId), () -> "건별 결과가 누락됐습니다: autoTransferId=" + autoTransferId))
                .toList();
    }

    // change()/cancel() 둘 다 findById() 이후 소유자 확인이 필요하다
    // — 존재 자체를 숨기기 위해 별도 코드(FORBIDDEN) 대신 findById 실패와 동일한 NOT_FOUND를 던진다
    private void requireOwned(AutoTransfer autoTransfer, Long customerId) {
        if (!autoTransfer.getCustomerId().equals(customerId)) {
            throw new BusinessException(AutoTransferErrorCode.NOT_FOUND);
        }
    }
}
