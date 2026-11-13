package com.shinhan.corebank.subscription.application.service;

import com.shinhan.corebank.account.api.MaturityAccountSettlementUseCase;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.subscription.api.TerminationPreviewResult;
import com.shinhan.corebank.subscription.api.TerminationPreviewUseCase;
import com.shinhan.corebank.subscription.application.port.in.InterestCalculationCommand;
import com.shinhan.corebank.subscription.application.port.in.InterestCalculationUseCase;
import com.shinhan.corebank.subscription.application.port.out.InterestPaymentHistoryPort;
import com.shinhan.corebank.subscription.domain.InterestResult;
import com.shinhan.corebank.subscription.domain.exception.SubscriptionErrorCode;
import com.shinhan.corebank.transfer.api.LedgerPrincipalBalanceQuery;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TerminationPreviewService implements TerminationPreviewUseCase {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MaturityTerminationValidationService maturityValidationService;
    private final EarlyTerminationValidationService earlyValidationService;
    private final InterestCalculationUseCase interestCalculationUseCase;
    private final InterestPaymentHistoryPort historyPort;
    private final MaturityAccountSettlementUseCase balanceUseCase;
    private final LedgerPrincipalBalanceQuery ledgerPrincipalBalanceQuery;
    private final Clock clock;

    @Override
    public TerminationPreviewResult previewMaturity(Long customerId, Long accountId) {

        var validated = maturityValidationService.validate(customerId, accountId);

        requireInterestNotPaid(accountId);

        var mode =
                switch (validated.calculationMode()) {
                    case SIMPLE -> InterestCalculationCommand.CalculationMode.SIMPLE;
                    case MONTHLY_COMPOUND -> InterestCalculationCommand.CalculationMode.MONTHLY_COMPOUND;
                };

        InterestResult interest = interestCalculationUseCase.calculate(new InterestCalculationCommand(
                accountId, validated.openedDate(), validated.maturityDate(), validated.annualRate(), mode));

        return createResult(
                customerId,
                accountId,
                validated.withdrawalAccountId(),
                "MATURITY",
                validated.timeDeposit(),
                validated.subscriptionAmount(),
                interest);
    }

    @Override
    public TerminationPreviewResult previewEarly(Long customerId, Long accountId) {

        var validated = earlyValidationService.validate(customerId, accountId);

        requireInterestNotPaid(accountId);

        InterestResult interest = interestCalculationUseCase.calculate(new InterestCalculationCommand(
                accountId,
                validated.openedDate(),
                validated.terminationDate(),
                validated.annualRate(),
                InterestCalculationCommand.CalculationMode.SIMPLE));

        return createResult(
                customerId,
                accountId,
                validated.withdrawalAccountId(),
                "EARLY_TERMINATION",
                validated.timeDeposit(),
                validated.subscriptionAmount(),
                interest);
    }

    private void requireInterestNotPaid(Long accountId) {
        if (historyPort.existsByAccountId(accountId)) {
            throw new BusinessException(SubscriptionErrorCode.INTEREST_ALREADY_PAID);
        }
    }

    private TerminationPreviewResult createResult(
            Long customerId,
            Long accountId,
            Long withdrawalAccountId,
            String type,
            boolean timeDeposit,
            Long subscriptionAmount,
            InterestResult interest) {

        // 이자 지급 전의 현재 상품 계좌 잔액
        long principal = balanceUseCase.requireCurrentBalance(accountId, customerId);

        // 실제 해지 서비스와 동일한 원금 정합성 검증
        if (timeDeposit) {
            if (subscriptionAmount == null || subscriptionAmount <= 0 || principal != subscriptionAmount) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
            }
        } else {
            long ledgerPrincipal = ledgerPrincipalBalanceQuery.sumNetAmountExcludingInterest(accountId);

            if (ledgerPrincipal < 0 || principal != ledgerPrincipal) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
            }
        }

        // 기존 InterestPaymentService의 정합성 조건
        if (interest.gross() > 0 && interest.net() == 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        long total;
        try {
            total = Math.addExact(principal, interest.net());
        } catch (ArithmeticException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, e);
        }

        if (total <= 0) {
            throw new BusinessException(
                    type.equals("MATURITY")
                            ? SubscriptionErrorCode.MATURITY_PAYOUT_AMOUNT_ZERO
                            : SubscriptionErrorCode.EARLY_TERMINATION_PAYOUT_ZERO);
        }

        OffsetDateTime calculatedAt = OffsetDateTime.now(clock.withZone(KST));

        return new TerminationPreviewResult(
                accountId,
                type,
                withdrawalAccountId,
                principal,
                interest.gross(),
                interest.tax(),
                interest.net(),
                total,
                calculatedAt.toLocalDate(),
                calculatedAt);
    }
}
