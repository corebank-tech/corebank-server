package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.subscription.api.EarlyTerminationResult;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 중도해지 결과. 금액은 원 단위. */
public record EarlyTerminationResponse(
        Long accountId,
        Long withdrawalAccountId,
        long principal,
        long grossInterest,
        long tax,
        long netInterest,
        long totalTransferred,
        String transactionNumber,
        LocalDate tradeDate,
        LocalDateTime closedAt) {
    public static EarlyTerminationResponse from(EarlyTerminationResult result) {
        return new EarlyTerminationResponse(
                result.accountId(),
                result.withdrawalAccountId(),
                result.principal(),
                result.grossInterest(),
                result.tax(),
                result.netInterest(),
                result.totalTransferred(),
                result.transactionNumber(),
                result.tradeDate(),
                result.closedAt());
    }
}
