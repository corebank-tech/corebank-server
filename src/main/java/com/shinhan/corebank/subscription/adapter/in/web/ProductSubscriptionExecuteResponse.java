package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionExecuteUseCase.ProductSubscriptionExecuteResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProductSubscriptionExecuteResponse(
        Long subscriptionId,
        Long accountId,
        String accountNumber,
        String productName,
        ProductGroup productGroup,
        Long subscriptionAmount,
        Integer termMonths,
        BigDecimal appliedRate,
        LocalDate openedDate,
        LocalDate maturityDate,
        Long expectedMaturityAmount,
        ProcessResultStatus status,
        String transactionNumber,
        LocalDateTime subscribedAt) {
    public static ProductSubscriptionExecuteResponse from(ProductSubscriptionExecuteResult result) {
        return new ProductSubscriptionExecuteResponse(
                result.subscriptionId(),
                result.accountId(),
                result.accountNumber(),
                result.productName(),
                result.productGroup(),
                result.subscriptionAmount(),
                result.termMonths(),
                result.appliedRate(),
                result.openedDate(),
                result.maturityDate(),
                result.expectedMaturityAmount(),
                result.status(),
                result.transactionNumber(),
                result.subscribedAt());
    }
}
