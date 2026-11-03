package com.shinhan.corebank.subscription.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 중도해지 처리 결과. 금액은 모두 원 단위이다. */
public record EarlyTerminationResult(
        Long accountId,
        Long withdrawalAccountId,
        long principal,
        long grossInterest,
        long tax,
        long netInterest,
        long totalTransferred,
        String transactionNumber,
        LocalDate tradeDate,
        LocalDateTime closedAt) {}
