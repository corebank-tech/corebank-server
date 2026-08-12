package com.shinhan.corebank.transfer.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountsForTransfer;
import com.shinhan.corebank.transfer.application.port.out.ResolvedPayee;
import com.shinhan.corebank.transfer.application.port.out.TransferSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferSequencePort;
import com.shinhan.corebank.transfer.domain.LedgerPair;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferSourceType;
import com.shinhan.corebank.transfer.domain.TransferType;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원장 기표 엔진의 유스케이스 구현체.
 * [계좌번호→ID 해석 → 계좌 락 획득 → 채번 → 원장 2행 INSERT → 잔액 UPDATE → 이체 기록]을
 * 하나의 트랜잭션 안에서 원자적으로 수행한다.
 * propagation을 REQUIRED로 명시한다 — REQUIRES_NEW로 두면 호출자(P5 자동이체 배치 등)가
 * 이 실행을 자신의 트랜잭션에 합류시킬 방법이 없어진다(피호출자 애노테이션이 항상 우선).
 * @Primary: MockTransferExecutionPort와 test/local 프로필에서 같이 뜨는 동안, 타입 기반으로
 * TransferExecutionUseCase를 주입받는 소비자(AutoTransferBatchItemProcessor 등)가 두 빈 중
 * 무엇을 받을지 모호해지는 것을 막는다. 실제 구현체가 나온 이상 기본 후보는 이쪽이어야 한다.
 */
@Service
@Primary
public class TransferExecutionService implements TransferExecutionUseCase {

    private static final long FEE = 0L; // 당행 이체 수수료 0 고정 (POL-028)

    private final AccountLockPort accountLockPort;
    private final TransferSequencePort transferSequencePort;
    private final TransferSavePort transferSavePort;
    private final LedgerSavePort ledgerSavePort;

    public TransferExecutionService(
            AccountLockPort accountLockPort,
            TransferSequencePort transferSequencePort,
            TransferSavePort transferSavePort,
            LedgerSavePort ledgerSavePort
    ) {
        this.accountLockPort = accountLockPort;
        this.transferSequencePort = transferSequencePort;
        this.transferSavePort = transferSavePort;
        this.ledgerSavePort = ledgerSavePort;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public TransferResult execute(TransferCommand command) {
        LocalDateTime now = LocalDateTime.now();

        ResolvedPayee payee = accountLockPort.resolvePayeeByAccountNumber(command.depositAccountNumber())
                .orElseThrow(() -> new BusinessException(TransferErrorCode.PAYEE_NOT_FOUND));

        LockedAccountsForTransfer locked =
                accountLockPort.lockForTransfer(command.withdrawalAccountId(), payee.accountId());

        String transactionNumber =
                transferSequencePort.nextTransactionNumber(now.toLocalDate(), command.channel());

        Transfer transfer = Transfer.create(
                transactionNumber,
                command.withdrawalAccountId(),
                payee.accountId(),
                command.depositAccountNumber(),
                payee.payeeName(),
                command.amount(),
                FEE,
                command.transferType(),
                command.channel(),
                resolveSourceType(command.transferType()),
                command.sourceId(),
                command.myPassbookMemo(),
                command.recipientPassbookMemo(),
                now
        );
        transfer = transferSavePort.save(transfer);

        long withdrawalBalanceAfter = Math.subtractExact(locked.withdrawal().balance(), command.amount());
        long depositBalanceAfter = Math.addExact(locked.deposit().balance(), command.amount());

        LedgerPair pair = LedgerPair.forTransfer(
                transfer.getTransferId(),
                transactionNumber,
                command.withdrawalAccountId(),
                withdrawalBalanceAfter,
                payee.accountId(),
                depositBalanceAfter,
                command.amount(),
                resolveTransactionType(command.transferType()),
                command.myPassbookMemo(),
                command.recipientPassbookMemo(),
                command.channel(),
                now
        );
        ledgerSavePort.save(pair);

        accountLockPort.debit(locked.withdrawal(), command.amount());
        accountLockPort.credit(locked.deposit(), command.amount());

        transfer.complete(withdrawalBalanceAfter, now);
        transfer = transferSavePort.save(transfer);

        return TransferResult.builder()
                .status(transfer.getStatus())
                .transactionNumber(transfer.getTransactionNumber())
                .transferredAt(transfer.getTransferredAt())
                .withdrawalBalanceAfter(transfer.getWithdrawalBalanceAfter())
                .build();
    }

    private TransferSourceType resolveSourceType(TransferType transferType) {
        return switch (transferType) {
            case SCHEDULED -> TransferSourceType.SCHEDULED;
            case AUTO -> TransferSourceType.AUTO;
            case IMMEDIATE -> null;
        };
    }

    private String resolveTransactionType(TransferType transferType) {
        return switch (transferType) {
            case IMMEDIATE -> "IMMEDIATE_TRANSFER";
            case SCHEDULED -> "SCHEDULED_TRANSFER";
            case AUTO -> "AUTO_TRANSFER";
        };
    }
}
