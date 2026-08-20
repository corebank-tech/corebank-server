package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryItem;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryResult;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.TransferChannel;

import java.time.LocalDateTime;
import java.util.List;

public record AccountTransactionResponse(
        SummaryResponse summary,
        int page,
        int size,
        long totalCount,
        int totalPages,
        List<TransactionItemResponse> items
) {

    public static AccountTransactionResponse from(
            LedgerHistoryResult result
    ) {
        return new AccountTransactionResponse(
                SummaryResponse.from(result),
                result.page() + 1,
                result.size(),
                result.totalCount(),
                result.totalPages(),
                result.items().stream()
                        .map(
                                TransactionItemResponse::from
                        )
                        .toList()
        );
    }

    public record SummaryResponse(
            long depositCount,
            long depositAmount,
            long withdrawalCount,
            long withdrawalAmount
    ) {

        private static SummaryResponse from(
                LedgerHistoryResult result
        ) {
            return new SummaryResponse(
                    result.summary()
                            .depositCount(),
                    result.summary()
                            .depositAmount(),
                    result.summary()
                            .withdrawalCount(),
                    result.summary()
                            .withdrawalAmount()
            );
        }
    }

    public record TransactionItemResponse(
            Long ledgerEntryId,
            String transactionNumber,
            LocalDateTime occurredAt,
            String transactionType,
            Long withdrawalAmount,
            Long depositAmount,
            String transactionContent,
            long balanceAfter,
            TransferChannel channel
    ) {

        private static TransactionItemResponse from(
                LedgerHistoryItem item
        ) {
            return new TransactionItemResponse(
                    item.ledgerEntryId(),
                    item.transactionNumber(),
                    item.occurredAt(),
                    item.transactionType(),
                    item.direction()
                                    == LedgerDirection.WITHDRAWAL
                            ? item.amount()
                            : null,
                    item.direction()
                                    == LedgerDirection.DEPOSIT
                            ? item.amount()
                            : null,
                    item.transactionContent(),
                    item.balanceAfter(),
                    item.channel()
            );
        }
    }
}