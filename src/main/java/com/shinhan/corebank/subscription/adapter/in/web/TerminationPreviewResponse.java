package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.subscription.api.TerminationPreviewResult;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TerminationPreviewResponse(
        Long accountId,
        String terminationType,
        Long withdrawalAccountId,
        long principal,
        long grossInterest,
        long tax,
        long netInterest,
        long totalExpectedAmount,
        LocalDate calculationDate,
        OffsetDateTime calculatedAt) {

    public static TerminationPreviewResponse from(TerminationPreviewResult result) {
        return new TerminationPreviewResponse(
                result.accountId(),
                result.terminationType(),
                result.withdrawalAccountId(),
                result.principal(),
                result.grossInterest(),
                result.tax(),
                result.netInterest(),
                result.totalExpectedAmount(),
                result.calculationDate(),
                result.calculatedAt());
    }
}
