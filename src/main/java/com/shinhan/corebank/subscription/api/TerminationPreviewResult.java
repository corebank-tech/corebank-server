package com.shinhan.corebank.subscription.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TerminationPreviewResult(
        Long accountId,
        TerminationType terminationType,
        Long withdrawalAccountId,
        long principal,
        long grossInterest,
        long tax,
        long netInterest,
        long totalExpectedAmount,
        LocalDate calculationDate,
        OffsetDateTime calculatedAt) {}
