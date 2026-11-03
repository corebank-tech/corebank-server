package com.shinhan.corebank.subscription.application.service;

import com.shinhan.corebank.account.api.AccountTransferLifecycleLockUseCase;
import com.shinhan.corebank.account.api.EarlyTerminationAccountSettlementUseCase;
import com.shinhan.corebank.account.api.MaturityAccountQueryUseCase;
import com.shinhan.corebank.account.api.MaturityAccountSettlementUseCase;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.autotransfer.api.AutoTransferAccountClosureUseCase;
import com.shinhan.corebank.business.api.BusinessDateGate;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.api.JournalDirection;
import com.shinhan.corebank.gl.api.JournalLine;
import com.shinhan.corebank.gl.api.JournalPostingUseCase;
import com.shinhan.corebank.gl.api.JournalRequest;
import com.shinhan.corebank.scheduledtransfer.api.ScheduledTransferAccountClosureUseCase;
import com.shinhan.corebank.subscription.api.EarlyTerminationCommand;
import com.shinhan.corebank.subscription.api.EarlyTerminationResult;
import com.shinhan.corebank.subscription.api.EarlyTerminationUseCase;
import com.shinhan.corebank.subscription.api.InterestPaymentCommand;
import com.shinhan.corebank.subscription.api.InterestPaymentReason;
import com.shinhan.corebank.subscription.api.InterestPaymentResult;
import com.shinhan.corebank.subscription.api.InterestPaymentUseCase;
import com.shinhan.corebank.subscription.application.port.out.AccountNumberQueryPort;
import com.shinhan.corebank.subscription.application.port.out.MaturityTerminationPasswordVerificationPort;
import com.shinhan.corebank.subscription.domain.exception.SubscriptionErrorCode;
import com.shinhan.corebank.transfer.api.DepositChannel;
import com.shinhan.corebank.transfer.api.DepositType;
import com.shinhan.corebank.transfer.api.LedgerDepositResult;
import com.shinhan.corebank.transfer.api.LedgerDepositUseCase;
import com.shinhan.corebank.transfer.api.LedgerPrincipalBalanceQuery;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PH-16 중도해지 원리금 처리. #844의 연결 자동·예약이체 정리 계약이 아직 없는 동안
 * HTTP 노출을 기본 비활성화한다(EarlyTerminationController).
 */
@Service
@RequiredArgsConstructor
public class EarlyTerminationService implements EarlyTerminationUseCase {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BusinessDateGate businessDateGate;
    private final EarlyTerminationValidationService validationService;
    private final MaturityTerminationPasswordVerificationPort passwordVerificationPort;
    private final InterestPaymentUseCase interestPaymentUseCase;
    private final MaturityAccountSettlementUseCase balanceUseCase;
    private final LedgerPrincipalBalanceQuery ledgerPrincipalBalanceQuery;
    private final LedgerDepositUseCase ledgerDepositUseCase;
    private final MaturityAccountQueryUseCase destinationUseCase;
    private final JournalPostingUseCase journalPostingUseCase;
    private final EarlyTerminationAccountSettlementUseCase settlementUseCase;
    private final AccountNumberQueryPort accountNumberQueryPort;
    private final AutoTransferAccountClosureUseCase autoTransferClosureUseCase;
    private final ScheduledTransferAccountClosureUseCase scheduledTransferClosureUseCase;
    private final AccountTransferLifecycleLockUseCase lifecycleLockUseCase;
    private final Clock clock;

    @Override
    @Transactional
    public EarlyTerminationResult terminate(EarlyTerminationCommand command) {
        businessDateGate.acquireSharedAndReadBusinessDate();
        EarlyTerminationValidationService.Validated validated = validationService.validate(command);
        passwordVerificationPort.verifyAndConsumeAccountPasswordToken(
                command.accountPasswordAuthToken(), command.customerId(), command.accountId());
        // 연결 이체 조회 이전에 상품·수취 계좌를 같은 순서로 잠근다.
        // 등록 트랜잭션과 중도해지 트랜잭션이 이 잠금을 기준으로 직렬화된다.
        lifecycleLockUseCase.lockForEarlyTermination(
                command.customerId(), validated.accountId(), validated.withdrawalAccountId());

        // 기존 가입 상품 계좌의 실제 계좌번호를 조회한다.
        // 입금계좌로 연결된 자동·예약이체도 찾아야 하므로 계좌번호가 필요하다.
        String accountNumber = accountNumberQueryPort
                .findAccountNumber(validated.accountId(), command.customerId())
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN));

        LocalDateTime cleanupAt = LocalDateTime.now(clock.withZone(KST));

        // 연결 자동·예약이체 정리도 중도해지와 같은 트랜잭션에서 수행한다.
        autoTransferClosureUseCase.terminateLinkedTransfers(validated.accountId(), accountNumber, cleanupAt);

        scheduledTransferClosureUseCase.cancelLinkedTransfers(validated.accountId(), accountNumber, cleanupAt);

        InterestPaymentResult interest = interestPaymentUseCase.pay(new InterestPaymentCommand(
                validated.accountId(),
                validated.openedDate(),
                validated.terminationDate(),
                validated.annualRate(),
                InterestPaymentCommand.CalculationMode.SIMPLE,
                DepositChannel.WB,
                InterestPaymentReason.TERMINATION));

        long total = balanceUseCase.requireCurrentBalance(validated.accountId(), command.customerId());
        if (total <= 0) {
            throw new BusinessException(SubscriptionErrorCode.EARLY_TERMINATION_PAYOUT_ZERO);
        }
        if (interest.net() < 0 || total < interest.net()) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        long principal = total - interest.net();
        if (validated.timeDeposit()) {
            if (validated.subscriptionAmount() == null
                    || validated.subscriptionAmount() <= 0
                    || principal != validated.subscriptionAmount()) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
            }
        } else {
            long ledgerPrincipal = ledgerPrincipalBalanceQuery.sumNetAmountExcludingInterest(validated.accountId());
            if (ledgerPrincipal < 0 || principal != ledgerPrincipal) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
            }
        }

        LedgerDepositResult transfer = ledgerDepositUseCase.transferBetween(
                validated.accountId(),
                validated.withdrawalAccountId(),
                total,
                DepositType.TERMINATION,
                DepositChannel.WB);
        if (transfer.transactionNumber() == null || transfer.tradeDate() == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        destinationUseCase.requireWithdrawalDestinationForUpdate(validated.withdrawalAccountId(), command.customerId());
        journalPostingUseCase.post(new JournalRequest(
                GlTxType.TERMINATION,
                transfer.transactionNumber(),
                transfer.tradeDate(),
                List.of(
                        new JournalLine("20100", JournalDirection.DEBIT, total),
                        new JournalLine("20100", JournalDirection.CREDIT, total))));

        LocalDateTime closedAt = LocalDateTime.now(clock.withZone(KST));
        settlementUseCase.closeEarly(validated.accountId(), command.customerId(), closedAt, transfer.tradeDate());
        return new EarlyTerminationResult(
                validated.accountId(),
                validated.withdrawalAccountId(),
                principal,
                interest.gross(),
                interest.tax(),
                interest.net(),
                total,
                transfer.transactionNumber(),
                transfer.tradeDate(),
                closedAt);
    }
}
