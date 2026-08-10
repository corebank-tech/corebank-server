package com.shinhan.corebank.transfer.domain;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerPair {

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
            LocalDateTime occurredAt
    ) {
        if (withdrawalAccountId == null || depositAccountId == null || occurredAt == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        if (amount <= 0) {
            throw new BusinessException(TransferErrorCode.INVALID_AMOUNT);
        }
        if (withdrawalAccountId.equals(depositAccountId)) {
            throw new BusinessException(TransferErrorCode.SAME_ACCOUNT_TRANSFER);
        }

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
