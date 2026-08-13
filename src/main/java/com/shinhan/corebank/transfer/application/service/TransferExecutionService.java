package com.shinhan.corebank.transfer.application.service;

import java.time.Clock;
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
import com.shinhan.corebank.transfer.application.port.out.TransferBalances;
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
 * [계좌번호→ID 해석 → 계좌 락 획득 → 계좌번호 재검증 → 채번 → 잔액 UPDATE → 원장 2행 INSERT
 * → 이체 기록]을 하나의 트랜잭션 안에서 원자적으로 수행한다. 계좌번호 재검증: 락 없이 조회한
 * 입금계좌번호→ID 매핑이 락 획득 시점까지 유효한지 다시 확인해 TOCTOU를 막는다.
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
    private final Clock clock;

    public TransferExecutionService(
            AccountLockPort accountLockPort,
            TransferSequencePort transferSequencePort,
            TransferSavePort transferSavePort,
            LedgerSavePort ledgerSavePort,
            Clock clock
    ) {
        this.accountLockPort = accountLockPort;
        this.transferSequencePort = transferSequencePort;
        this.transferSavePort = transferSavePort;
        this.ledgerSavePort = ledgerSavePort;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public TransferResult execute(TransferCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);

        ResolvedPayee payee = accountLockPort.resolvePayeeByAccountNumber(command.depositAccountNumber())
                .orElseThrow(() -> new BusinessException(TransferErrorCode.PAYEE_NOT_FOUND));

        LockedAccountsForTransfer locked =
                accountLockPort.lockForTransfer(command.withdrawalAccountId(), payee.accountId());

        // 계좌번호 조회(락 없음)와 락 획득 사이에 입금계좌 매핑이 바뀌지 않았는지 재확인한다.
        if (!command.depositAccountNumber().equals(locked.deposit().accountNumber())) {
            throw new BusinessException(TransferErrorCode.PAYEE_NOT_FOUND);
        }

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

        TransferBalances balances = accountLockPort.applyTransfer(locked, command.amount());

        LedgerPair pair = LedgerPair.forTransfer(
                transfer.getTransferId(),
                transactionNumber,
                command.withdrawalAccountId(),
                balances.withdrawalBalanceAfter(),
                payee.accountId(),
                balances.depositBalanceAfter(),
                command.amount(),
                resolveTransactionType(command.transferType()),
                command.myPassbookMemo(),
                command.recipientPassbookMemo(),
                command.channel(),
                now
        );
        ledgerSavePort.save(pair);

        transfer.complete(balances.withdrawalBalanceAfter(), now);
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
