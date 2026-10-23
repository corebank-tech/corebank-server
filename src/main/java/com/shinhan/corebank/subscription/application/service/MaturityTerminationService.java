package com.shinhan.corebank.subscription.application.service;

import com.shinhan.corebank.account.api.MaturityAccountQueryUseCase;
import com.shinhan.corebank.account.api.MaturityAccountSettlementUseCase;
import com.shinhan.corebank.business.api.BusinessDateGate;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.api.JournalDirection;
import com.shinhan.corebank.gl.api.JournalLine;
import com.shinhan.corebank.gl.api.JournalPostingUseCase;
import com.shinhan.corebank.gl.api.JournalRequest;
import com.shinhan.corebank.subscription.api.InterestPaymentCommand;
import com.shinhan.corebank.subscription.api.InterestPaymentReason;
import com.shinhan.corebank.subscription.api.InterestPaymentResult;
import com.shinhan.corebank.subscription.api.InterestPaymentUseCase;
import com.shinhan.corebank.subscription.api.MaturityTerminationCommand;
import com.shinhan.corebank.subscription.api.MaturityTerminationResult;
import com.shinhan.corebank.subscription.api.MaturityTerminationUseCase;
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

@Service
@RequiredArgsConstructor
public class MaturityTerminationService implements MaturityTerminationUseCase {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BusinessDateGate businessDateGate;
    private final MaturityTerminationValidationService validationService;
    private final InterestPaymentUseCase interestPaymentUseCase;
    private final MaturityAccountSettlementUseCase settlementUseCase;
    private final LedgerDepositUseCase ledgerDepositUseCase;
    private final MaturityAccountQueryUseCase maturityAccountQueryUseCase;
    private final MaturityTerminationPasswordVerificationPort passwordVerificationPort;
    private final JournalPostingUseCase journalPostingUseCase;
    private final LedgerPrincipalBalanceQuery ledgerPrincipalBalanceQuery;
    private final Clock clock;

    @Override
    @Transactional
    public MaturityTerminationResult terminate(MaturityTerminationCommand command) {

        // 1. 다른 계좌 잠금·DB 조회보다 먼저 영업일 Gate를 획득한다.
        businessDateGate.acquireSharedAndReadBusinessDate();

        // 2. 만기해지 가능 여부 및 가입 당시 계약 조건 검증
        MaturityTerminationValidationService.Validated validated = validationService.validate(command);

        passwordVerificationPort.verifyAndConsumeAccountPasswordToken(
                command.accountPasswordAuthToken(), command.customerId(), command.accountId());

        Long accountId = validated.accountId();
        Long withdrawalAccountId = validated.withdrawalAccountId();

        // 3. 이자 계산 방식 변환
        InterestPaymentCommand.CalculationMode calculationMode =
                switch (validated.calculationMode()) {
                    case SIMPLE -> InterestPaymentCommand.CalculationMode.SIMPLE;
                    case MONTHLY_COMPOUND -> InterestPaymentCommand.CalculationMode.MONTHLY_COMPOUND;
                };

        // 4. PH-14 이자 지급
        // 세후이자 입금, 원천세 GL 기표, 지급 이력 저장을 수행한다.
        InterestPaymentResult interest = interestPaymentUseCase.pay(new InterestPaymentCommand(
                accountId,
                validated.openedDate(),
                validated.maturityDate(),
                validated.annualRate(),
                calculationMode,
                DepositChannel.WB,
                InterestPaymentReason.MATURITY));

        // 5. 이자 지급 후 실제 상품 계좌 잔액 조회
        // JPA 전체 Account 엔티티 대신 잔액 전용 조회를 사용한다.
        long totalTransferred = settlementUseCase.requireCurrentBalance(accountId, command.customerId());

        // PH-99 transferBetween()은 0원 이체를 허용하지 않는다.
        if (totalTransferred <= 0) {
            throw new BusinessException(SubscriptionErrorCode.MATURITY_PAYOUT_AMOUNT_ZERO);
        }

        // 이자 지급액보다 총 잔액이 적으면 정합성 오류다.
        if (interest.net() < 0 || totalTransferred < interest.net()) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        long principal = totalTransferred - interest.net();

        if (validated.timeDeposit()) {
            // 정기예금: 가입 당시 계약 원금과 비교
            if (validated.subscriptionAmount() == null
                    || validated.subscriptionAmount() <= 0
                    || principal != validated.subscriptionAmount()) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
            }
        } else {
            // 정기적금: 실제 원장상 누적 원금과 비교
            // 현재는 만기 시 1회 이자 지급을 전제한다.
            // 중간 이자 지급을 지원하면 과거 INTEREST 누적액도
            // 실제 원금 산출에서 제외하도록 계산식을 변경해야 한다.
            long ledgerPrincipal = ledgerPrincipalBalanceQuery.sumNetAmountExcludingInterest(accountId);

            if (ledgerPrincipal < 0 || principal != ledgerPrincipal) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
            }
        }

        // 6. 상품 계좌 -> 고객 수취 계좌로 원리금 전액 이체
        LedgerDepositResult transfer = ledgerDepositUseCase.transferBetween(
                accountId, withdrawalAccountId, totalTransferred, DepositType.MATURITY, DepositChannel.WB);

        if (transfer.transactionNumber() == null || transfer.tradeDate() == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        // 7. 계좌 잠금 이후 수취 계좌 소유권 및 등록 상태 재검증
        // 실패하면 지금까지 수행한 이자 지급과 원리금 이체도 롤백된다.
        maturityAccountQueryUseCase.requireWithdrawalDestinationForUpdate(withdrawalAccountId, command.customerId());

        // MATURITY 원리금 원장과 같은 거래번호·거래일로 GL 전표 기표
        journalPostingUseCase.post(new JournalRequest(
                GlTxType.MATURITY,
                transfer.transactionNumber(),
                transfer.tradeDate(),
                List.of(
                        new JournalLine("20100", JournalDirection.DEBIT, totalTransferred),
                        new JournalLine("20100", JournalDirection.CREDIT, totalTransferred))));

        // 8. 상품 계좌 최신 상태 조회 및 CLOSED 전이
        LocalDateTime closedAt = LocalDateTime.now(clock.withZone(KST));

        settlementUseCase.closeMatured(accountId, command.customerId(), closedAt, transfer.tradeDate());

        // 9. 최종 결과 반환
        return new MaturityTerminationResult(
                accountId,
                withdrawalAccountId,
                principal,
                interest.gross(),
                interest.tax(),
                interest.net(),
                totalTransferred,
                transfer.transactionNumber(),
                transfer.tradeDate(),
                closedAt);
    }
}
