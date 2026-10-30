package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.subscription.api.MaturityTerminationResult;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 고객에게 반환할 만기해지 결과. 금액은 원 단위 정수이다. */
public record MaturityTerminationResponse(
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

    public static MaturityTerminationResponse from(MaturityTerminationResult result) {
        return new MaturityTerminationResponse(
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
