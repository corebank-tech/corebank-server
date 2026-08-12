package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.subscription.domain.ProductSubscription;
import com.shinhan.corebank.subscription.domain.ProductSubscriptionResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProductSubscriptionResultResponse(
        Long subscriptionId,
        Long accountId,
        String accountNumber,
        Long productId,
        String productName,
        ProductGroup productGroup,
        Long subscriptionAmount,
        Integer termMonths,
        BigDecimal appliedRate,
        LocalDate openedDate,
        LocalDate maturityDate,
        Long expectedMaturityAmount,
        String status,
        String transactionNumber,
        LocalDateTime subscribedAt,
        AutoTransferPrefill autoTransferPrefill
) {
    public static ProductSubscriptionResultResponse from(ProductSubscriptionResult result) {
        ProductSubscription s = result.getSubscription();

        boolean canCreateAutoTransfer = result.getProductGroup() == ProductGroup.SAVINGS
                && s.getStatus() == ProcessResultStatus.SUCCESS
                && s.getAccountId() != null
                && result.getAccountNumber() != null;

        return new ProductSubscriptionResultResponse(
                s.getSubscriptionId(),
                s.getAccountId(),
                result.getAccountNumber() == null ? null : MaskingUtil.maskAccountNumber(result.getAccountNumber()),
                s.getProductId(),
                result.getProductName(),
                result.getProductGroup(),
                s.getSubscriptionAmount(),
                (int) s.getTermMonths(),
                s.getAppliedRate(),
                s.getOpenedDate(),
                s.getMaturityDate(),
                s.getExpectedMaturityAmount(),
                s.getStatus().name(),
                s.getTransactionNumber(),
                s.getSubscribedAt(),
                canCreateAutoTransfer ? AutoTransferPrefill.from(s, result.getAccountNumber()) : null
        );
    }

    public record AutoTransferPrefill(
            Long withdrawalAccountId,
            String depositAccountNumber,
            Long amount,
            Integer cycleMonths,
            LocalDate endDate
    ) {
        static AutoTransferPrefill from(ProductSubscription s, String rawAccountNumber) {
            return new AutoTransferPrefill(
                    s.getWithdrawalAccountId(),
                    rawAccountNumber, // 마스킹 안 함 — §3-5 참고
                    s.getSubscriptionAmount(),
                    1,
                    s.getMaturityDate());
        }
    }
}
