package com.shinhan.corebank.transfer.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 원장 기표 엔진의 유스케이스 구현체.
 * [계좌번호→ID 해석 → 채번 → (독립 트랜잭션) 계좌 락 획득 → 계좌번호 재검증 → 잔액검증
 * → 잔액 UPDATE → 원장 2행 INSERT → 이체 완료 기록]을 수행한다.
 *
 * <p>기표(락 획득~완료 기록)는 {@code requiresNewTransactionTemplate}으로 별도 트랜잭션에서
 * 실행한다. 실패하면 그 트랜잭션만 독립적으로, 동기적으로 롤백되고(원장 0행 보장), 그 시점에
 * transaction_number 유니크 키가 확실히 반납된 뒤 catch 블록에서 ERROR 확정 행을 새로 INSERT
 * 한다. execute() 자체를 REQUIRED로 감쌌던 Day4 설계에서는, 실패 시 "이미 커밋한 PROCESSING
 * 행을 UPDATE로 ERROR 전환"이 REPEATABLE READ 스냅샷·FK 공유락 때문에 안전하게 되지 않아
 * REQUIRES_NEW로 전환했다 — 호출자(P5 자동이체 배치 등)가 자신의 트랜잭션에 이 실행을 합류시킬
 * 수 없다는 트레이드오프가 있지만, 실패 기록 INSERT 자체는 호출자 트랜잭션이 있으면 거기 합류해
 * 커밋되므로(REQUIRED, TransferSavePort 기본) 호출자 쪽 원자성은 유지된다.
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
    private final TransactionTemplate requiresNewTransactionTemplate;

    public TransferExecutionService(
            AccountLockPort accountLockPort,
            TransferSequencePort transferSequencePort,
            TransferSavePort transferSavePort,
            LedgerSavePort ledgerSavePort,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.accountLockPort = accountLockPort;
        this.transferSequencePort = transferSequencePort;
        this.transferSavePort = transferSavePort;
        this.ledgerSavePort = ledgerSavePort;
        this.clock = clock;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public TransferResult execute(TransferCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);

        ResolvedPayee payee = accountLockPort.resolvePayeeByAccountNumber(command.depositAccountNumber())
                .orElseThrow(() -> new BusinessException(TransferErrorCode.PAYEE_NOT_FOUND));

        String transactionNumber =
                transferSequencePort.nextTransactionNumber(now.toLocalDate(), command.channel());

        // 실패해도 ERROR로 남길 수 있도록, DB에 아직 저장되지 않은(transferId=null) 상태로
        // 들고 있는다. 아래 트랜잭션이 실패해 롤백되더라도 이 자바 객체 자체는 영향받지 않는다.
        Transfer created = Transfer.create(
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

        try {
            Transfer completed = requiresNewTransactionTemplate.execute(status -> {
                Transfer transfer = transferSavePort.save(created);

                LockedAccountsForTransfer locked =
                        accountLockPort.lockForTransfer(command.withdrawalAccountId(), payee.accountId());

                // 계좌번호 조회(락 없음)와 락 획득 사이에 입금계좌 매핑이 바뀌지 않았는지 재확인한다.
                if (!command.depositAccountNumber().equals(locked.deposit().accountNumber())) {
                    throw new BusinessException(TransferErrorCode.PAYEE_NOT_FOUND);
                }

                if (locked.withdrawal().balance() < command.amount()) {
                    throw new BusinessException(TransferErrorCode.INSUFFICIENT_BALANCE);
                }

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
                return transferSavePort.save(transfer);
            });

            return TransferResult.builder()
                    .status(completed.getStatus())
                    .transactionNumber(completed.getTransactionNumber())
                    .transferredAt(completed.getTransferredAt())
                    .withdrawalBalanceAfter(completed.getWithdrawalBalanceAfter())
                    .build();
        } catch (BusinessException e) {
            return failTransfer(created, e.getErrorCode().getCode(), e.getMessage());
        } catch (RuntimeException e) {
            failTransfer(created, CommonErrorCode.INTERNAL_ERROR.getCode(), CommonErrorCode.INTERNAL_ERROR.getMessage());
            throw e;
        }
    }

    /**
     * 이체 시도를 ERROR로 확정해 새 행으로 남긴다. 위 트랜잭션이 이미 롤백되어 같은
     * transaction_number를 가진 PROCESSING 행은 존재하지 않으므로 새 INSERT로 처리된다.
     */
    private TransferResult failTransfer(Transfer created, String errorCode, String errorMessage) {
        created.fail(errorCode, errorMessage);
        transferSavePort.save(created);

        return TransferResult.builder()
                .status(ProcessResultStatus.ERROR)
                .transactionNumber(created.getTransactionNumber())
                .errorCode(errorCode)
                .errorMessage(errorMessage)
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
