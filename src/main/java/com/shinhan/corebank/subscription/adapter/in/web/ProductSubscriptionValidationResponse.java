package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.subscription.domain.SubscriptionValidation;
import com.shinhan.corebank.subscription.domain.SubscriptionViolation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProductSubscriptionValidationResponse(
        Boolean valid,
        List<ViolationItem> violations,
        ProductGroup productGroup,
        BigDecimal baseRate,
        BigDecimal preferentialRate,
        BigDecimal appliedRate,
        LocalDate maturityDate,
        Long expectedPrincipal,
        Long expectedInterest,
        Long expectedMaturityAmount,
        String withdrawalAccountNumber,
        Long withdrawalAccountBalance
) {
    public static ProductSubscriptionValidationResponse from(SubscriptionValidation validation) {
        return new ProductSubscriptionValidationResponse(
                validation.isValid(),
                validation.getViolations().stream().map(ViolationItem::from).toList(),
                validation.getProductGroup(),
                validation.getBaseRate(),
                validation.getPreferentialRate(),
                validation.getAppliedRate(),
                validation.getMaturityDate(),
                validation.getExpectedPrincipal(),
                validation.getExpectedInterest(),
                validation.getExpectedMaturityAmount(),
                validation.getWithdrawalAccountNumber(),
                validation.getWithdrawalAccountBalance());
    }

    public record ViolationItem(String field, String code, String reason) {
        static ViolationItem from(SubscriptionViolation violation) {
            return new ViolationItem(violation.field(), violation.code(), violation.reason());
        }
    }
}
