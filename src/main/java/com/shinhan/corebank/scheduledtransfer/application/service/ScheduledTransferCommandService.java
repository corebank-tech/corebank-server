package com.shinhan.corebank.scheduledtransfer.application.service;

import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.limit.domain.exception.LmtErrorCode;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferCancelCommand;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferCancelResult;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
    public List<ScheduledTransferCancelResult> cancel(ScheduledTransferCancelCommand command) {
        LocalDate today = LocalDate.now(clock);

        // 건별 사전검증 — OTP는 성공 시 즉시 소비되므로 취소 가능 여부를 먼저 전부 가린다(otp_integration_guide.md §9).
        // 여기서 걸러진 건은 예외가 아니라 건별 실패 결과로 남고, 나머지 건의 취소를 막지 않는다.
        Map<Long, ScheduledTransferCancelResult> results = new HashMap<>();
        List<ScheduledTransfer> owned = new ArrayList<>();
        List<ScheduledTransfer> cancelable = new ArrayList<>();
        for (Long scheduledTransferId : command.scheduledTransferIds()) {
            Optional<ScheduledTransfer> found = scheduledTransferPersistencePort.findById(scheduledTransferId);
            // 미존재와 타인 소유를 구분하지 않는다 — scheduledTransferId도 순차 증가 PK라 스캐닝 위험이 있다(api_conventions.md §8-3)
            if (found.isEmpty() || !found.get().getCustomerId().equals(command.customerId())) {
                results.put(scheduledTransferId,
                        ScheduledTransferCancelResult.failure(scheduledTransferId, ScheduledTransferErrorCode.NOT_FOUND));
                continue;
            }
            ScheduledTransfer scheduledTransfer = found.get();
            owned.add(scheduledTransfer);
            // 이미 취소된 건 재요청은 멱등 성공 처리
            if (scheduledTransfer.getStatus() == ScheduledTransferStatus.CANCELED) {
                results.put(scheduledTransferId, ScheduledTransferCancelResult.success(scheduledTransfer));
                continue;
            }
            if (!scheduledTransfer.getStatus().isCancelable()) {
                results.put(scheduledTransferId, ScheduledTransferCancelResult.failure(
                        scheduledTransferId, ScheduledTransferErrorCode.NOT_IN_WAITING_STATUS));
                continue;
            }
            // 예정일 당일 여부
            if (!scheduledTransfer.getScheduledDate().isAfter(today)) {
                results.put(scheduledTransferId, ScheduledTransferCancelResult.failure(
                        scheduledTransferId, ScheduledTransferErrorCode.CANNOT_CANCEL_ON_EXECUTION_DATE));
                continue;
            }
            cancelable.add(scheduledTransfer);
        }

        // 출금계좌 혼합 여부는 상태와 무관한 요청 단위 계약이므로 소유가 확인된 전체를 기준으로 먼저 막는다.
        // cancelable만 검사하면 "이미 취소된 건이 다른 계좌"인 조합이 계약을 빠져나간다.
        requireSingleWithdrawalAccount(owned);

        // 실제로 취소할 건이 하나도 없으면 인증 토큰을 소비하지 않는다 — 고객이 OTP를 다시 발급받지 않아도 되도록
        if (cancelable.isEmpty()) {
            return orderedResults(command.scheduledTransferIds(), results);
        }

        // 위에서 단일 계좌임을 확인했으므로 어느 건의 출금계좌를 써도 같다
        authTokenVerificationPort.verify(command.accountPasswordAuthToken(),
                cancelable.getFirst().getWithdrawalAccountId(), "SCHEDULED_TRANSFER_CANCEL");
        // OTP 토큰에는 요청한 id 조합 전체가 묶여 있다 — 취소 가능한 건만 추려서 넘기면
        // 발급 시점 거래정보와 어긋나 OTP0102가 난다
        scheduledTransferOtpVerificationPort.verifyCancelAndConsume(command.otpAuthToken(), command.customerId(),
                command.scheduledTransferIds());

        for (ScheduledTransfer scheduledTransfer : cancelable) {
            scheduledTransfer.cancel(LocalDateTime.now(clock));
            ScheduledTransfer saved = scheduledTransferPersistencePort.save(scheduledTransfer);
            auditLogService.record(saved.getCustomerId(), null, AuditEventType.SCHEDULED_TRANSFER_INFO_CHANGE,
                    command.requestIp(), true, Map.of("scheduledTransferId", saved.getScheduledTransferId(), "action", "cancel"));
            results.put(saved.getScheduledTransferId(), ScheduledTransferCancelResult.success(saved));
        }

        return orderedResults(command.scheduledTransferIds(), results);
    }

    // accountPasswordAuthToken은 계좌 하나에 묶여 발급된다(api_conventions.md §6-3).
    // 출금계좌가 섞인 조합은 토큰 하나로 인증할 수 없으므로 건별 실패가 아니라 요청 자체를 거부한다.
    private void requireSingleWithdrawalAccount(List<ScheduledTransfer> owned) {
        long distinctWithdrawalAccounts = owned.stream()
                .map(ScheduledTransfer::getWithdrawalAccountId)
                .distinct()
                .count();
        if (distinctWithdrawalAccounts > 1) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "다건 취소는 같은 출금계좌의 예약이체만 함께 요청할 수 있습니다.");
        }
    }

    // 요청한 id 순서 그대로 건별 결과를 정렬해 돌려준다 — 화면이 선택 목록과 결과를 짝지을 수 있도록
    private List<ScheduledTransferCancelResult> orderedResults(List<Long> scheduledTransferIds,
                                                               Map<Long, ScheduledTransferCancelResult> results) {
        return scheduledTransferIds.stream()
                .map(scheduledTransferId -> Objects.requireNonNull(results.get(scheduledTransferId),
                        () -> "건별 결과가 누락됐습니다: scheduledTransferId=" + scheduledTransferId))
                .toList();
    }
}
