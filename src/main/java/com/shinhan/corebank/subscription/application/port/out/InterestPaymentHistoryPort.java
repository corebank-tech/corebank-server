package com.shinhan.corebank.subscription.application.port.out;

import com.shinhan.corebank.subscription.api.InterestPaymentReason;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface InterestPaymentHistoryPort {

    boolean existsByAccountId(Long accountId);

    void record(InterestPaymentRecord payment);

    record InterestPaymentRecord(
            Long accountId,
            InterestPaymentReason reason,
            long gross,
            long tax,
            long net,
            String transactionNumber,
            LocalDate tradeDate,
            LocalDateTime paidAt) {}
}
