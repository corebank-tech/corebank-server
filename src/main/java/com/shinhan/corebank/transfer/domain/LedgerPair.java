package com.shinhan.corebank.transfer.domain;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
        if (amount <= 0) {
            throw new IllegalArgumentException("원장 기표 금액은 0보다 커야 합니다."); // 수정 필요
        }
        if (withdrawalAccountId.equals(depositAccountId)) {
            throw new IllegalArgumentException("출금 계좌와 입금 계좌는 동일할 수 없습니다.");
        }

        LocalDateTime truncatedOccurredAt = occurredAt != null ? occurredAt.truncatedTo(ChronoUnit.MICROS) : null;

        // 출금 1행 (WITHDRAWAL, 양수 금액)
        LedgerEntry withdrawal = LedgerEntry.builder()
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
