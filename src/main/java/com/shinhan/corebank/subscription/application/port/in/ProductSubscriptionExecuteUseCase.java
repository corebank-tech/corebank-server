package com.shinhan.corebank.subscription.application.port.in;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.product.domain.ProductGroup;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ProductSubscriptionExecuteUseCase {
    ProductSubscriptionExecuteResult execute(ProductSubscriptionExecuteCommand command);

    record ProductSubscriptionExecuteCommand(
            Long customerId,
            Long productId,
            Long subscriptionAmount,
            Integer termMonths,
            Long withdrawalAccountId,
            String newAccountPassword,
            String newAccountPasswordConfirm,
            String accountPasswordAuthToken,
            String otpAuthToken,
            List<AgreedTerms> agreedTerms,
            List<String> satisfiedConditionCodes
    ) {
        public record AgreedTerms(Long termsId, String version) {
        }
    }

    record ProductSubscriptionExecuteResult(
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
            LocalDateTime subscribedAt
    ) {
    }
}
