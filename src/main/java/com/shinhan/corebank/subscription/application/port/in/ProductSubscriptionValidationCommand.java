package com.shinhan.corebank.subscription.application.port.in;

import java.util.List;

public record ProductSubscriptionValidationCommand(
        Long customerId,
        Long productId,
        Long subscriptionAmount,
        Integer termMonths,
        Long withdrawalAccountId,
        List<AgreedTerms> agreedTerms,
        List<String> satisfiedConditionCodes
) {
    public record AgreedTerms(Long termsId, String version) {
    }
}
