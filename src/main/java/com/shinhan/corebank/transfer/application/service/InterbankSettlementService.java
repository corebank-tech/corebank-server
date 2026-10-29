package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.business.api.BusinessDateGate;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.ErrorCode;
import com.shinhan.corebank.gl.api.JournalPostingUseCase;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankOutcome;
import com.shinhan.corebank.transfer.application.port.out.InterbankReply;
import com.shinhan.corebank.transfer.application.port.out.InterbankTransferPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerLookupPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccount;
import com.shinhan.corebank.transfer.application.port.out.TransferSavePort;
import com.shinhan.corebank.transfer.application.port.out.WithdrawalAccountDetail;
import com.shinhan.corebank.transfer.domain.LedgerEntry;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// 처리 불명 타행 건을 응답대로 확정한다. 송신 직후와 조회거래가 같이 쓰고, 행 락 + TIMEOUT 재확인으로 한 번만 반영한다.
@Service
public class InterbankSettlementService {

    static final String LEDGER_TRANSACTION_TYPE = "INTERBANK_TRANSFER";
    private static final String REFUND_MEMO = "타행이체반환";

    private final InterbankTransferPort interbankTransferPort;
    private final TransferSavePort transferSavePort;
    private final AccountLockPort accountLockPort;
    private final LedgerLookupPort ledgerLookupPort;
    private final LedgerSavePort ledgerSavePort;
    private final JournalPostingUseCase journalPostingUseCase;
    private final BusinessDateGate businessDateGate;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public InterbankSettlementService(
            InterbankTransferPort interbankTransferPort,
            TransferSavePort transferSavePort,
            AccountLockPort accountLockPort,
            LedgerLookupPort ledgerLookupPort,
            LedgerSavePort ledgerSavePort,
            JournalPostingUseCase journalPostingUseCase,
            BusinessDateGate businessDateGate,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.interbankTransferPort = interbankTransferPort;
        this.transferSavePort = transferSavePort;
        this.accountLockPort = accountLockPort;
        this.ledgerLookupPort = ledgerLookupPort;
        this.ledgerSavePort = ledgerSavePort;
        this.journalPostingUseCase = journalPostingUseCase;
        this.businessDateGate = businessDateGate;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // 결과를 모르거나(UNKNOWN) 이미 확정된 건이면 빈 값이다.
    public Optional<Transfer> settle(String transactionNumber, InterbankReply reply) {
        if (reply.outcome() == InterbankOutcome.UNKNOWN) {
            return Optional.empty();
        }
        return requiresNewTransactionTemplate.execute(status -> interbankTransferPort
                .findByTransactionNumberForUpdate(transactionNumber)
                .filter(Transfer::isUnsettledInterbank)
                .map(transfer -> settleLocked(transfer, reply)));
    }

    private Transfer settleLocked(Transfer transfer, InterbankReply reply) {
        LedgerEntry debit = ledgerLookupPort
                .findInterbankDebit(transfer.getTransactionNumber())
                .orElseThrow(() -> new IllegalStateException(
                        "타행 출금 원장이 없습니다 - transactionNumber = " + transfer.getTransactionNumber()));
        // 확정한 날의 영업일로 해소 전표를 세운다(정정 거래와 같은 규칙, transfer_seam.md 정책 6).
        LocalDate tradeDate = businessDateGate.acquireSharedAndReadBusinessDate();
        LocalDateTime now = LocalDateTime.now(clock);

        if (reply.outcome() == InterbankOutcome.APPROVED) {
            journalPostingUseCase.post(
                    InterbankJournal.approved(transfer.getTransactionNumber(), tradeDate, transfer.getAmount()));
            transfer.settleApproved(reply.payeeName(), debit.getBalanceAfter(), now);
        } else {
            refund(transfer, debit, tradeDate, now);
            ErrorCode errorCode = failureCode(reply.outcome());
            transfer.settleFailed(errorCode.getCode(), errorCode.getMessage());
        }
        Transfer settled = transferSavePort.save(transfer);
        publishSettled(settled, now);
        return settled;
    }

    // 계좌가 그사이 정지·해지됐어도 돈은 돌려준다 — 고객 거래가 아니라 상태를 보지 않는다.
    private void refund(Transfer transfer, LedgerEntry debit, LocalDate tradeDate, LocalDateTime now) {
        LockedAccount locked = accountLockPort.lockForCredit(transfer.getWithdrawalAccountId());
        long balanceAfter = accountLockPort.applyCredit(locked, transfer.getAmount());
        ledgerSavePort.saveRefund(LedgerEntry.forRefund(debit, balanceAfter, REFUND_MEMO, now, tradeDate), debit);
        journalPostingUseCase.post(
                InterbankJournal.refunded(transfer.getTransactionNumber(), tradeDate, transfer.getAmount()));
    }

    private static ErrorCode failureCode(InterbankOutcome outcome) {
        return switch (outcome) {
            case ACCOUNT_NOT_FOUND -> TransferErrorCode.PAYEE_NOT_FOUND;
            case ACCOUNT_UNAVAILABLE -> TransferErrorCode.PAYEE_ACCOUNT_SUSPENDED;
            case NOT_PROCESSED -> TransferErrorCode.INTERBANK_NOT_PROCESSED;
            case APPROVED, UNKNOWN -> throw new BusinessException(TransferErrorCode.INVALID_STATUS_TRANSITION);
        };
    }

    // 타행 확정 시점에도 이체 확정 이벤트를 낸다(tasks.md PH-33-②).
    private void publishSettled(Transfer transfer, LocalDateTime occurredAt) {
        Long customerId = accountLockPort
                .findWithdrawalAccountDetail(transfer.getWithdrawalAccountId())
                .map(WithdrawalAccountDetail::customerId)
                .orElse(null);
        eventPublisher.publishEvent(
                TransferSettledFactory.create(transfer, customerId, LEDGER_TRANSACTION_TYPE, occurredAt));
    }
}
