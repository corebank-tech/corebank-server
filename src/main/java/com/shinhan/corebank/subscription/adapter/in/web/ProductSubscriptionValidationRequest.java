package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionValidationCommand;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionValidationCommand.AgreedTerms;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ProductSubscriptionValidationRequest(
        @NotNull Long productId,
        @NotNull @Positive Long subscriptionAmount,
        @NotNull @Positive Integer termMonths,
        @NotNull Long withdrawalAccountId,
        @NotNull List<@Valid AgreedTermsItem> agreedTerms,
        List<String> satisfiedConditionCodes) {
    // agreedTerms는 null이면 안 되지만(요청 형식 오류), 빈 배열([])은 "아직 아무 약관에도
    // 동의하지 않은 상태로 미리 검증"하는 정상 케이스이기 때문에 @NotEmpty가 아니라 @NotNull이다.
    public ProductSubscriptionValidationCommand toCommand(Long customerId) {
        List<AgreedTerms> agreed = agreedTerms.stream()
                .map(item -> new AgreedTerms(item.termsId(), item.version()))
                .toList();
        List<String> satisfiedCodes = satisfiedConditionCodes == null ? List.of() : satisfiedConditionCodes;
        return new ProductSubscriptionValidationCommand(
                customerId, productId, subscriptionAmount, termMonths, withdrawalAccountId, agreed, satisfiedCodes);
    }

    public record AgreedTermsItem(@NotNull Long termsId, @NotNull String version) {}
}
