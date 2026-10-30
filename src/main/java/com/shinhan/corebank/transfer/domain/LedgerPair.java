package com.shinhan.corebank.transfer.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerPair {

    /** ledger_entry.transaction_type 중 상품가입 초입금을 나타내는 값(schema_reference.md). */
    private static final String PRODUCT_SUBSCRIPTION_TYPE = "PRODUCT_SUBSCRIPTION";
    /** 반대기표(정정 체인 취소정정)의 transaction_type. */
    private static final String REVERSAL_TYPE = "REVERSAL";

    private static final String REVERSAL_MEMO = "취소정정";

    private final LedgerEntry withdrawalEntry;
    private final LedgerEntry depositEntry;

    /**
     * 이체용 2행 복식기표 원장 쌍 생성 정적 팩토리 메서드
     */
    public static LedgerPair forTransfer(
            Long transferId,
            String transactionNumber,
            Long withdrawalAccountId,
            long withdrawalBalanceAfter,
            Long depositAccountId,
            long depositBalanceAfter,
            long amount,
            String transactionType,
            String myPassbookMemo,
            String recipientPassbookMemo,
            TransferChannel channel,
            LocalDateTime occurredAt,
            LocalDate tradeDate) {
        TransferValidations.requireNonNull(transferId, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonBlank(transactionType, CommonErrorCode.REQUIRED_FIELD_MISSING);

        return create(
                transferId,
                transactionNumber,
                withdrawalAccountId,
                withdrawalBalanceAfter,
                depositAccountId,
                depositBalanceAfter,
                amount,
                transactionType,
                myPassbookMemo,
                recipientPassbookMemo,
                channel,
                occurredAt,
                tradeDate,
                null,
                null);
    }

    /**
     * 상품가입 초입금용 2행 복식기표 원장 쌍 생성 정적 팩토리 메서드.
     * 이체가 아닌 기표라 transfer 행이 존재하지 않으므로 transfer_id를 NULL로 남긴다
     * (schema_reference.md의 ledger_entry.transfer_id 컬럼 설명). transaction_type은
     * 호출자가 고를 여지 없이 PRODUCT_SUBSCRIPTION으로 고정한다.
     */
    public static LedgerPair forProductSubscription(
            String transactionNumber,
            Long withdrawalAccountId,
            long withdrawalBalanceAfter,
            Long depositAccountId,
            long depositBalanceAfter,
            long amount,
            String myPassbookMemo,
            String recipientPassbookMemo,
            TransferChannel channel,
            LocalDateTime occurredAt,
            LocalDate tradeDate) {
        return create(
                null,
                transactionNumber,
                withdrawalAccountId,
                withdrawalBalanceAfter,
                depositAccountId,
                depositBalanceAfter,
                amount,
                PRODUCT_SUBSCRIPTION_TYPE,
                myPassbookMemo,
                recipientPassbookMemo,
                channel,
                occurredAt,
                tradeDate,
                null,
                null);
    }

    /**
     * 예적금 만기·해지 입금용 2행(상품 계좌 출금 → 고객 계좌 입금). 이체가 아니라 transfer_id는 NULL이고,
     * transaction_type은 입금 계약이 넘긴 업무 구분(MATURITY·TERMINATION)을 그대로 쓴다.
     */
    public static LedgerPair forDeposit(
            String transactionNumber,
            Long fromAccountId,
            long fromBalanceAfter,
            Long toAccountId,
            long toBalanceAfter,
            long amount,
            String transactionType,
            String passbookMemo,
            TransferChannel channel,
            LocalDateTime occurredAt,
            LocalDate tradeDate) {
        TransferValidations.requireNonBlank(transactionType, CommonErrorCode.REQUIRED_FIELD_MISSING);

        return create(
                null,
                transactionNumber,
                fromAccountId,
                fromBalanceAfter,
                toAccountId,
                toBalanceAfter,
                amount,
                transactionType,
                passbookMemo,
                passbookMemo,
                channel,
                occurredAt,
                tradeDate,
                null,
                null);
    }

    /**
     * 정정 체인 취소정정용 반대기표 2행. 원거래 출금계좌로 돈을 되돌리므로 방향이 원거래와 반대다.
     * 각 행의 reversal_id는 같은 계좌의 원거래 행을 가리킨다. 원거래 행의 reversed 표시는 저장 쪽이 세운다.
     */
    public static LedgerPair forReversal(
            Long reversalTransferId,
            String transactionNumber,
            LedgerEntry originalWithdrawal,
            LedgerEntry originalDeposit,
            long payeeBalanceAfter,
            long originalWithdrawalAccountBalanceAfter,
            TransferChannel channel,
            LocalDateTime occurredAt,
            LocalDate tradeDate) {
        TransferValidations.requireNonNull(reversalTransferId, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonNull(
                originalWithdrawal.getLedgerEntryId(), CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonNull(originalDeposit.getLedgerEntryId(), CommonErrorCode.REQUIRED_FIELD_MISSING);
        // 바꿔 넣으면 돈이 되돌아가지 않고 같은 방향으로 한 번 더 간다.
        if (originalWithdrawal.getDirection() != LedgerDirection.WITHDRAWAL
                || originalDeposit.getDirection() != LedgerDirection.DEPOSIT) {
            throw new BusinessException(TransferErrorCode.NOT_CORRECTABLE);
        }

        return create(
                reversalTransferId,
                transactionNumber,
                originalDeposit.getAccountId(),
                payeeBalanceAfter,
                originalWithdrawal.getAccountId(),
                originalWithdrawalAccountBalanceAfter,
                originalWithdrawal.getAmount(),
                REVERSAL_TYPE,
                REVERSAL_MEMO,
                REVERSAL_MEMO,
                channel,
                occurredAt,
                tradeDate,
                originalDeposit.getLedgerEntryId(),
                originalWithdrawal.getLedgerEntryId());
    }

    // transferId·transactionType·reversalId만 다르고 나머지 검증·조립은 네 팩토리가 완전히 같다.
    private static LedgerPair create(
            Long transferId,
            String transactionNumber,
            Long withdrawalAccountId,
            long withdrawalBalanceAfter,
            Long depositAccountId,
            long depositBalanceAfter,
            long amount,
            String transactionType,
            String myPassbookMemo,
            String recipientPassbookMemo,
            TransferChannel channel,
            LocalDateTime occurredAt,
            LocalDate tradeDate,
            Long withdrawalReversalId,
            Long depositReversalId) {
        TransferValidations.requireAccountIdsPresent(withdrawalAccountId, depositAccountId);
        TransferValidations.requireNonNull(occurredAt, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonNull(tradeDate, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonBlank(transactionNumber, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonNull(channel, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requirePositiveAmount(amount);
        TransferValidations.requireDifferentAccounts(withdrawalAccountId, depositAccountId);

        LocalDateTime truncatedOccurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);

        // 출금 1행 (WITHDRAWAL, 양수 금액)
        LedgerEntry withdrawal = LedgerEntry.builder()
                .transferId(transferId)
                .accountId(withdrawalAccountId)
                .transactionNumber(transactionNumber)
                .direction(LedgerDirection.WITHDRAWAL)
                .amount(amount)
                .balanceAfter(withdrawalBalanceAfter)
                .transactionType(transactionType)
                .transactionContent(myPassbookMemo)
                .channel(channel)
                .reversed(false)
                .reversalId(withdrawalReversalId)
                .occurredAt(truncatedOccurredAt)
                .tradeDate(tradeDate)
                .build();

        // 입금 1행 (DEPOSIT, 양수 금액)
        LedgerEntry deposit = LedgerEntry.builder()
                .transferId(transferId)
                .accountId(depositAccountId)
                .transactionNumber(transactionNumber)
                .direction(LedgerDirection.DEPOSIT)
                .amount(amount)
                .balanceAfter(depositBalanceAfter)
                .transactionType(transactionType)
                .transactionContent(recipientPassbookMemo)
                .channel(channel)
                .reversed(false)
                .reversalId(depositReversalId)
                .occurredAt(truncatedOccurredAt)
                .tradeDate(tradeDate)
                .build();
        return new LedgerPair(withdrawal, deposit);
    }
}
