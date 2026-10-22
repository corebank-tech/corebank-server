package com.shinhan.corebank.subscription.application.service;

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
import com.shinhan.corebank.subscription.application.port.in.InterestCalculationCommand;
import com.shinhan.corebank.subscription.application.port.in.InterestCalculationUseCase;
import com.shinhan.corebank.subscription.application.port.out.InterestPaymentHistoryPort;
import com.shinhan.corebank.subscription.application.port.out.InterestPaymentHistoryPort.InterestPaymentRecord;
import com.shinhan.corebank.subscription.domain.InterestResult;
import com.shinhan.corebank.subscription.domain.exception.SubscriptionErrorCode;
import com.shinhan.corebank.transfer.api.DepositType;
import com.shinhan.corebank.transfer.api.LedgerDepositResult;
import com.shinhan.corebank.transfer.api.LedgerDepositUseCase;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterestPaymentService implements InterestPaymentUseCase {

    private static final String INTEREST_EXPENSE = "50100";
    private static final String DEPOSIT_LIABILITY = "20100";
    private static final String WITHHOLDING_TAX = "20200";

    private final BusinessDateGate businessDateGate;
    private final InterestCalculationUseCase interestCalculationUseCase;
    private final LedgerDepositUseCase ledgerDepositUseCase;
    private final JournalPostingUseCase journalPostingUseCase;
    private final InterestPaymentHistoryPort historyPort;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public InterestPaymentResult pay(InterestPaymentCommand command) {
        validate(command);

        // 계좌 잠금이나 원장 조회보다 먼저 영업일 Gate를 확보한다.
        businessDateGate.acquireSharedAndReadBusinessDate();

        if (historyPort.existsByAccountId(command.accountId())) {
            throw new BusinessException(SubscriptionErrorCode.INTEREST_ALREADY_PAID);
        }

        InterestCalculationCommand calculationCommand = new InterestCalculationCommand(
                command.accountId(),
                command.openedDate(),
                command.toExclusive(),
                command.annualRate(),
                toCalculationMode(command.calculationMode()));

        InterestResult interest = interestCalculationUseCase.calculate(calculationCommand);

        // 정상 세율에서는 세전이자가 양수이면 세후이자도 양수다.
        // 비정상 설정에서 세전이자만 발생한 채 GL을 누락하지 않도록 방어한다.
        if (interest.gross() > 0 && interest.net() == 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        LedgerDepositResult ledger = null;

        if (interest.net() > 0) {
            // 원장에는 세후이자만 입금한다.
            ledger = ledgerDepositUseCase.credit(
                    command.accountId(), interest.net(), DepositType.INTEREST, command.channel());

            // GL은 원장의 거래번호와 실제 거래일을 그대로 사용한다.
            journalPostingUseCase.post(new JournalRequest(
                    GlTxType.INTEREST, ledger.transactionNumber(), ledger.tradeDate(), createJournalLines(interest)));
        }

        // 이력도 원장·GL과 동일 트랜잭션에서 저장한다.
        historyPort.record(new InterestPaymentRecord(
                command.accountId(),
                command.reason(),
                interest.gross(),
                interest.tax(),
                interest.net(),
                ledger == null ? null : ledger.transactionNumber(),
                ledger == null ? null : ledger.tradeDate(),
                LocalDateTime.now(clock)));

        return new InterestPaymentResult(
                interest.gross(),
                interest.tax(),
                interest.net(),
                ledger == null ? null : ledger.transactionNumber(),
                ledger == null ? null : ledger.tradeDate());
    }

    private static List<JournalLine> createJournalLines(InterestResult interest) {
        List<JournalLine> lines = new ArrayList<>();

        // 세전이자 비용 인식
        lines.add(new JournalLine(INTEREST_EXPENSE, JournalDirection.DEBIT, interest.gross()));
        lines.add(new JournalLine(DEPOSIT_LIABILITY, JournalDirection.CREDIT, interest.gross()));

        // 원천징수: 예수금 감소 및 원천세예수금 증가
        if (interest.tax() > 0) {
            lines.add(new JournalLine(DEPOSIT_LIABILITY, JournalDirection.DEBIT, interest.tax()));
            lines.add(new JournalLine(WITHHOLDING_TAX, JournalDirection.CREDIT, interest.tax()));
        }

        return lines;
    }

    private static InterestCalculationCommand.CalculationMode toCalculationMode(
            InterestPaymentCommand.CalculationMode mode) {
        return switch (mode) {
            case SIMPLE -> InterestCalculationCommand.CalculationMode.SIMPLE;
            case MONTHLY_COMPOUND -> InterestCalculationCommand.CalculationMode.MONTHLY_COMPOUND;
        };
    }

    private static void validate(InterestPaymentCommand command) {
        if (command == null
                || command.accountId() == null
                || command.openedDate() == null
                || command.toExclusive() == null
                || command.annualRate() == null
                || command.calculationMode() == null
                || command.channel() == null
                || command.reason() == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        if (command.accountId() <= 0 || command.annualRate().signum() < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        if (command.openedDate().isAfter(command.toExclusive())) {
            throw new BusinessException(CommonErrorCode.INVALID_DATE_RANGE);
        }

        // PH-16 중도해지는 복리 상품이어도 단리로 재계산한다.
        if (command.reason() == InterestPaymentReason.TERMINATION
                && command.calculationMode() != InterestPaymentCommand.CalculationMode.SIMPLE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
