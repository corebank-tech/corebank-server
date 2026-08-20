package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionExecuteUseCase.ProductSubscriptionExecuteCommand;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionExecuteUseCase.ProductSubscriptionExecuteCommand.AgreedTerms;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ProductSubscriptionExecuteRequest(
        @NotNull Long productId,
        @NotNull @Positive Long subscriptionAmount,
        @NotNull @Positive Integer termMonths,
        @NotNull Long withdrawalAccountId,
        @NotNull @Pattern(regexp = "^[0-9]{4}$") String newAccountPassword,
        @NotNull @Pattern(regexp = "^[0-9]{4}$") String newAccountPasswordConfirm,
        @NotBlank String accountPasswordAuthToken,
        @NotBlank String otpAuthToken,
        // 상품에 필수 약관이 하나도 없으면 빈 배열이 정상 요청이다(ProductSubscriptionValidationRequest와
        // 동일한 이유로 @NotEmpty가 아니라 @NotNull).
        @NotNull List<@Valid AgreedTermsItem> agreedTerms,
        List<String> satisfiedConditionCodes
) {
    public ProductSubscriptionExecuteCommand toCommand(Long customerId) {
        List<AgreedTerms> agreed = agreedTerms.stream()
                .map(item -> new AgreedTerms(item.termsId(), item.version()))
                .toList();
        List<String> satisfiedCodes = satisfiedConditionCodes == null ? List.of() : satisfiedConditionCodes;
        return new ProductSubscriptionExecuteCommand(
                customerId, productId, subscriptionAmount, termMonths, withdrawalAccountId,
                newAccountPassword, newAccountPasswordConfirm, accountPasswordAuthToken, otpAuthToken,
                agreed, satisfiedCodes);
    }

    public record AgreedTermsItem(@NotNull Long termsId, @NotNull String version) {
    }
}
