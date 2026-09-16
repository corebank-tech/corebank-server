package com.shinhan.corebank.transfer.domain;

import com.shinhan.corebank.common.exception.CommonErrorCode;
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
            LocalDateTime occurredAt) {
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
                occurredAt);
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
            LocalDateTime occurredAt) {
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
                occurredAt);
    }

    // transferId·transactionType만 다르고 나머지 검증·조립은 두 팩토리가 완전히 같다.
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
            LocalDateTime occurredAt) {
        TransferValidations.requireAccountIdsPresent(withdrawalAccountId, depositAccountId);
        TransferValidations.requireNonNull(occurredAt, CommonErrorCode.REQUIRED_FIELD_MISSING);
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
                .occurredAt(truncatedOccurredAt)
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
                .occurredAt(truncatedOccurredAt)
                .build();
        return new LedgerPair(withdrawal, deposit);
    }
}
