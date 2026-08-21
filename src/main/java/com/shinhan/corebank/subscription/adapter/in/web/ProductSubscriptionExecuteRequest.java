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
        @NotNull List<@Valid AgreedTermsItem> agreedTerms
        // satisfiedConditionCodes는 여기 없다 — /validation 미리보기와 달리 실행 API는 이 값을
        // 그대로 신뢰하면 안 된다(PR #147 합의, ProductSubscriptionValidationService
        // .calculatePreferentialRate() 주석 참고). 서버가 실제로 재검증할 근거가 아직 없어
        // 실행 시점엔 우대금리를 적용하지 않는다(코드리뷰 반영, #256 계열 후속과 별개로 처리).
) {
    public ProductSubscriptionExecuteCommand toCommand(Long customerId) {
        List<AgreedTerms> agreed = agreedTerms.stream()
                .map(item -> new AgreedTerms(item.termsId(), item.version()))
                .toList();
        return new ProductSubscriptionExecuteCommand(
                customerId, productId, subscriptionAmount, termMonths, withdrawalAccountId,
                newAccountPassword, newAccountPasswordConfirm, accountPasswordAuthToken, otpAuthToken,
                agreed);
    }

    public record AgreedTermsItem(@NotNull Long termsId, @NotNull String version) {
    }
}
