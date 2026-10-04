package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.application.port.in.TransferCorrectionUseCase;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerLookupPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountsForTransfer;
import com.shinhan.corebank.transfer.application.port.out.PostedLedgerPair;
import com.shinhan.corebank.transfer.application.port.out.ResolvedPayee;
import com.shinhan.corebank.transfer.application.port.out.TransferBalances;
import com.shinhan.corebank.transfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.transfer.application.port.out.TransferSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferSequencePort;
import com.shinhan.corebank.transfer.domain.CorrectionType;
import com.shinhan.corebank.transfer.domain.LedgerPair;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정정 체인 구현체 (PH-80). 취소정정·정상거래 모두
 * [원거래 조회 → 채번 → 계좌 락 → 정정 이체 INSERT(자리 차지) → 상태·잔액 검증 → 잔액 이동 → 원장]을
 * 한 트랜잭션으로 수행한다. 어느 단계든 실패하면 전부 롤백된다.
 *
 * 고객 화면에서 시작되는 거래가 아니라 채널은 BT이고 이체 한도를 차감하지 않는다.
 * 알림 이벤트(TransferSettled)는 발행하지 않는다 — 정정 전용 알림은 P1과 따로 정한다.
 */
@Service
@RequiredArgsConstructor
public class TransferCorrectionService implements TransferCorrectionUseCase {

    private static final TransferChannel CHANNEL = TransferChannel.BT;
    private static final String REVERSAL_MEMO = "취소정정";

    private final TransferLookupPort transferLookupPort;
    private final TransferSavePort transferSavePort;
    private final TransferSequencePort transferSequencePort;
    private final AccountLockPort accountLockPort;
    private final LedgerLookupPort ledgerLookupPort;
    private final LedgerSavePort ledgerSavePort;
    private final Clock clock;

    @Override
    @Transactional
    public TransferReversalResult reverse(String originalTransactionNumber) {
        Transfer original = transferLookupPort
                .findByTransactionNumber(originalTransactionNumber)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.TRANSACTION_NOT_FOUND));

        LocalDateTime requestedAt = LocalDateTime.now(clock);
        String transactionNumber = transferSequencePort.nextTransactionNumber(requestedAt.toLocalDate(), CHANNEL);

        // 돈은 원거래 입금계좌(payee)에서 원거래 출금계좌로 돌아간다. 락은 lockForTransfer가 ID 오름차순으로 잡는다.
        LockedAccountsForTransfer locked =
                accountLockPort.lockForTransfer(original.getDepositAccountId(), original.getWithdrawalAccountId());

        // 원장·잔액 시각은 락을 쥔 뒤에 찍는다(schema_reference.md ledger_entry.occurred_at, #545).
        LocalDateTime executedAt = LocalDateTime.now(clock);

        original.invalidate(executedAt);
        Transfer reversal = claimReversal(original, locked, transactionNumber, executedAt);
        requireActiveAccounts(locked);
        if (locked.withdrawal().balance() < original.getAmount()) {
            throw new BusinessException(TransferErrorCode.REVERSAL_INSUFFICIENT_BALANCE);
        }

        PostedLedgerPair originalLedger = ledgerLookupPort
                .findPostedPair(original.getTransactionNumber())
                .orElseThrow(() -> new BusinessException(TransferErrorCode.NOT_CORRECTABLE));

        transferSavePort.save(original);
        TransferBalances balances = accountLockPort.applyTransfer(locked, original.getAmount(), executedAt);
        ledgerSavePort.saveReversal(
                LedgerPair.forReversal(
                        reversal.getTransferId(),
                        transactionNumber,
                        originalLedger.withdrawal(),
                        originalLedger.deposit(),
                        balances.withdrawalBalanceAfter(),
                        balances.depositBalanceAfter(),
                        CHANNEL,
                        executedAt),
                originalLedger);
        reversal.complete(balances.withdrawalBalanceAfter(), executedAt);
        transferSavePort.save(reversal);

        return new TransferReversalResult(transactionNumber, original.getTransactionNumber(), original.getAmount());
    }

    @Override
    @Transactional
    public TransferRepostResult repost(String originalTransactionNumber, long correctedAmount) {
        Transfer original = transferLookupPort
                .findByTransactionNumber(originalTransactionNumber)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.TRANSACTION_NOT_FOUND));
        // 취소정정보다 정상거래가 먼저 생기면 원거래와 정상거래 금액이 둘 다 나간 상태가 된다.
        if (!original.isInvalidated()) {
            throw new BusinessException(TransferErrorCode.NOT_CORRECTABLE);
        }

        LocalDateTime requestedAt = LocalDateTime.now(clock);
        String transactionNumber = transferSequencePort.nextTransactionNumber(requestedAt.toLocalDate(), CHANNEL);
        LockedAccountsForTransfer locked =
                accountLockPort.lockForTransfer(original.getWithdrawalAccountId(), original.getDepositAccountId());
        LocalDateTime executedAt = LocalDateTime.now(clock);

        Transfer repost = claimRepost(original, correctedAmount, transactionNumber, executedAt);
        requireActiveAccounts(locked);
        if (locked.withdrawal().balance() < correctedAmount) {
            throw new BusinessException(TransferErrorCode.INSUFFICIENT_BALANCE);
        }

        TransferBalances balances = accountLockPort.applyTransfer(locked, correctedAmount, executedAt);
        ledgerSavePort.save(LedgerPair.forTransfer(
                repost.getTransferId(),
                transactionNumber,
                original.getWithdrawalAccountId(),
                balances.withdrawalBalanceAfter(),
                original.getDepositAccountId(),
                balances.depositBalanceAfter(),
                correctedAmount,
                original.getTransferType().ledgerTransactionType(),
                original.getMyPassbookMemo(),
                original.getRecipientPassbookMemo(),
                CHANNEL,
                executedAt));
        repost.complete(balances.withdrawalBalanceAfter(), executedAt);
        transferSavePort.save(repost);

        return new TransferRepostResult(transactionNumber, original.getTransactionNumber(), correctedAmount);
    }

    // 정상거래는 원래 하려던 거래라 예금주명·통장 메모를 원거래 스냅샷 그대로 쓴다.
    private Transfer claimRepost(
            Transfer original, long correctedAmount, String transactionNumber, LocalDateTime executedAt) {
        Transfer repost = Transfer.create(
                transactionNumber,
                original.getWithdrawalAccountId(),
                original.getDepositAccountId(),
                original.getDepositAccountNumber(),
                original.getPayeeName(),
                correctedAmount,
                0L,
                original.getTransferType(),
                CHANNEL,
                null,
                null,
                null,
                original.getMyPassbookMemo(),
                original.getRecipientPassbookMemo(),
                executedAt);
        repost.linkToOriginal(CorrectionType.REPOST, original.getTransferId());
        return claim(repost);
    }

    private void requireActiveAccounts(LockedAccountsForTransfer locked) {
        if (locked.withdrawal().status() != LockedAccountStatus.ACTIVE) {
            throw new BusinessException(TransferErrorCode.WITHDRAWAL_ACCOUNT_SUSPENDED);
        }
        if (locked.deposit().status() != LockedAccountStatus.ACTIVE) {
            throw new BusinessException(TransferErrorCode.PAYEE_ACCOUNT_SUSPENDED);
        }
    }

    // 동시 정정은 락 전 스냅샷으로 앞선 검사를 통과하므로, 검증보다 먼저 INSERT해 유니크 제약이 막게 한다.
    private Transfer claim(Transfer correction) {
        try {
            return transferSavePort.save(correction);
        } catch (DataIntegrityViolationException alreadyCorrected) {
            throw new BusinessException(TransferErrorCode.NOT_CORRECTABLE);
        }
    }

    private Transfer claimReversal(
            Transfer original, LockedAccountsForTransfer locked, String transactionNumber, LocalDateTime executedAt) {
        String refundAccountNumber = locked.deposit().accountNumber();
        String refundAccountHolder = accountLockPort
                .resolvePayeeByAccountNumber(refundAccountNumber)
                .map(ResolvedPayee::payeeName)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.ACCOUNT_LOCK_TARGET_NOT_FOUND));

        Transfer reversal = Transfer.create(
                transactionNumber,
                original.getDepositAccountId(),
                original.getWithdrawalAccountId(),
                refundAccountNumber,
                refundAccountHolder,
                original.getAmount(),
                0L,
                original.getTransferType(),
                CHANNEL,
                null,
                null,
                null,
                REVERSAL_MEMO,
                REVERSAL_MEMO,
                executedAt);
        reversal.linkToOriginal(CorrectionType.REVERSAL, original.getTransferId());
        return claim(reversal);
    }
}
