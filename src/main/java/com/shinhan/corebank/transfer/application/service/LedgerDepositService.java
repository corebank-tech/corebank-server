package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.api.DepositChannel;
import com.shinhan.corebank.transfer.api.DepositType;
import com.shinhan.corebank.transfer.api.LedgerDepositResult;
import com.shinhan.corebank.transfer.api.LedgerDepositUseCase;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccount;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountsForTransfer;
import com.shinhan.corebank.transfer.application.port.out.TransferBalances;
import com.shinhan.corebank.transfer.application.port.out.TransferSequencePort;
import com.shinhan.corebank.transfer.domain.LedgerEntry;
import com.shinhan.corebank.transfer.domain.LedgerPair;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예적금 이자·만기·해지 입금 계약 구현체(#595). [채번 → 계좌 락 → 상태·잔액 재검증 → 잔액 반영 → 원장 INSERT]를
 * {@link ProductSubscriptionDepositService}와 같은 순서로 수행한다.
 *
 * {@code @Transactional}은 기본 전파(REQUIRED)다 — 호출자의 상태 전이와 한 트랜잭션이어야 호출자가 롤백할 때
 * 입금도 함께 롤백된다.
 */
@Service
@RequiredArgsConstructor
public class LedgerDepositService implements LedgerDepositUseCase {

    private static final Set<DepositType> TRANSFER_BETWEEN_TYPES =
            Set.of(DepositType.MATURITY, DepositType.TERMINATION);
    private static final Set<DepositType> CREDIT_TYPES = Set.of(DepositType.INTEREST);
    // 만기 계좌도 해지 전까지는 돈이 들고 나야 한다 — 만기 이자 입금과 원리금 출금.
    private static final Set<LockedAccountStatus> PRODUCT_ACCOUNT_STATUSES =
            Set.of(LockedAccountStatus.ACTIVE, LockedAccountStatus.MATURED);

    private final AccountLockPort accountLockPort;
    private final TransferSequencePort transferSequencePort;
    private final LedgerSavePort ledgerSavePort;
    private final Clock clock;

    @Override
    @Transactional
    public LedgerDepositResult transferBetween(
            long fromAccountId, long toAccountId, long amount, DepositType type, DepositChannel channel) {
        requireType(type, TRANSFER_BETWEEN_TYPES);
        TransferChannel transferChannel = toTransferChannel(channel);
        String transactionNumber = nextTransactionNumber(transferChannel);

        LockedAccountsForTransfer locked = accountLockPort.lockForTransfer(fromAccountId, toAccountId);
        // 원장·잔액 시각은 락을 쥔 뒤에 찍어야 같은 계좌의 occurred_at 순서가 잔액 반영 순서와 같아진다(#545).
        LocalDateTime executedAt = LocalDateTime.now(clock);

        if (!PRODUCT_ACCOUNT_STATUSES.contains(locked.withdrawal().status())) {
            throw new BusinessException(TransferErrorCode.WITHDRAWAL_ACCOUNT_SUSPENDED);
        }
        if (locked.deposit().status() != LockedAccountStatus.ACTIVE) {
            throw new BusinessException(TransferErrorCode.PAYEE_ACCOUNT_SUSPENDED);
        }
        if (locked.withdrawal().balance() < amount) {
            throw new BusinessException(TransferErrorCode.INSUFFICIENT_BALANCE);
        }

        TransferBalances balances = accountLockPort.applyTransfer(locked, amount, executedAt);

        ledgerSavePort.save(LedgerPair.forDeposit(
                transactionNumber,
                fromAccountId,
                balances.withdrawalBalanceAfter(),
                toAccountId,
                balances.depositBalanceAfter(),
                amount,
                type.name(),
                passbookMemo(type),
                transferChannel,
                executedAt));

        return new LedgerDepositResult(transactionNumber, balances.depositBalanceAfter());
    }

    @Override
    @Transactional
    public LedgerDepositResult credit(long accountId, long amount, DepositType type, DepositChannel channel) {
        requireType(type, CREDIT_TYPES);
        TransferChannel transferChannel = toTransferChannel(channel);
        String transactionNumber = nextTransactionNumber(transferChannel);

        LockedAccount locked = accountLockPort.lockForCredit(accountId);
        LocalDateTime executedAt = LocalDateTime.now(clock);

        if (!PRODUCT_ACCOUNT_STATUSES.contains(locked.status())) {
            throw new BusinessException(TransferErrorCode.PAYEE_ACCOUNT_SUSPENDED);
        }

        long balanceAfter = accountLockPort.applyCredit(locked, amount);

        ledgerSavePort.saveCredit(LedgerEntry.forCredit(
                transactionNumber,
                accountId,
                balanceAfter,
                amount,
                type.name(),
                passbookMemo(type),
                transferChannel,
                executedAt));

        return new LedgerDepositResult(transactionNumber, balanceAfter);
    }

    private static void requireType(DepositType type, Set<DepositType> allowed) {
        if (type == null || !allowed.contains(type)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    // 채번은 REQUIRES_NEW로 독립 커밋된다 — 이 트랜잭션이 롤백돼도 번호만 빈다. 기준일은 상품가입 입금과 같게 맞춘다.
    private String nextTransactionNumber(TransferChannel channel) {
        return transferSequencePort.nextTransactionNumber(
                LocalDateTime.now(clock).toLocalDate(), channel);
    }

    private static TransferChannel toTransferChannel(DepositChannel channel) {
        if (channel == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        return switch (channel) {
            case WB -> TransferChannel.WB;
            case BT -> TransferChannel.BT;
        };
    }

    // ledger_entry.transaction_content는 VARCHAR(10)이다.
    private static String passbookMemo(DepositType type) {
        return switch (type) {
            case MATURITY -> "만기해지";
            case TERMINATION -> "중도해지";
            case INTEREST -> "이자";
        };
    }
}
