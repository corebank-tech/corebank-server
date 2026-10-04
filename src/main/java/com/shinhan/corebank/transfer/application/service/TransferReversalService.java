package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.application.port.in.TransferReversalUseCase;
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
 * 정정 체인 취소정정 구현체 (PH-80).
 * [원거래 조회 → 채번 → 계좌 락 → 원거래 무효화 → 취소정정 이체 INSERT → 상태·잔액 검증 → 잔액 이동 → 원장 반대기표]를
 * 한 트랜잭션으로 수행한다. 어느 단계든 실패하면 전부 롤백돼 원거래는 유효한 채로 남는다.
 *
 * 고객 화면에서 시작되는 거래가 아니라 채널은 BT이고 이체 한도를 차감하지 않는다.
 * 알림 이벤트(TransferSettled)는 발행하지 않는다 — 정정 전용 알림은 P1과 따로 정한다.
 */
@Service
@RequiredArgsConstructor
public class TransferReversalService implements TransferReversalUseCase {

    private static final TransferChannel CHANNEL = TransferChannel.BT;
    private static final String PASSBOOK_MEMO = "취소정정";

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

        if (locked.withdrawal().status() != LockedAccountStatus.ACTIVE) {
            throw new BusinessException(TransferErrorCode.WITHDRAWAL_ACCOUNT_SUSPENDED);
        }
        if (locked.deposit().status() != LockedAccountStatus.ACTIVE) {
            throw new BusinessException(TransferErrorCode.PAYEE_ACCOUNT_SUSPENDED);
        }
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

    // 잔액 검증보다 먼저 취소정정 행을 넣어 자리를 차지한다. 동시 정정은 둘 다 무효화 검사를 통과할 수 있어
    // (락 전에 읽은 원거래 스냅샷) uk_transfer_correction만 확실히 막는다 — 잔액 검증이 먼저면 이유가 TRF0306으로 바뀐다.
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
                PASSBOOK_MEMO,
                PASSBOOK_MEMO,
                executedAt);
        reversal.linkToOriginal(CorrectionType.REVERSAL, original.getTransferId());
        try {
            return transferSavePort.save(reversal);
        } catch (DataIntegrityViolationException alreadyReversed) {
            throw new BusinessException(TransferErrorCode.NOT_CORRECTABLE);
        }
    }
}
