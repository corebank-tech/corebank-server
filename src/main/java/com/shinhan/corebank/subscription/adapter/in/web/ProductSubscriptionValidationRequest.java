package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionValidationCommand;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionValidationCommand.AgreedTerms;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ProductSubscriptionValidationRequest(
        @Schema(description = "가입할 상품의 내부 식별자", example = "1") @NotNull Long productId,
        @Schema(description = "가입금액. 상품의 최소·최대 가입금액과 입력단위(amountUnit)를 만족해야 한다", example = "1000000") @NotNull @Positive
                Long subscriptionAmount,
        @Schema(description = "가입기간(월). 상품에 정의된 금리테이블의 기간 중 하나여야 한다", example = "12") @NotNull @Positive
                Integer termMonths,
        @Schema(description = "출금계좌(가입금 이체 및 향후 자동납입 계좌)의 내부 식별자", example = "101") @NotNull Long withdrawalAccountId,
        @Schema(description = "약관 동의 목록. 상품에 필수 약관이 없으면 빈 배열([])") @NotNull List<@Valid AgreedTermsItem> agreedTerms,
        @Schema(description = "고객이 충족했다고 신고한 우대조건 코드 목록. 미리보기 금리 계산에만 쓰이고 실행에는 반영되지 않음")
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

    public record AgreedTermsItem(
            @Schema(description = "동의한 약관의 내부 식별자", example = "3") @NotNull Long termsId,
            @Schema(description = "동의한 약관의 버전. 현재 버전과 다르면 TERMS_VERSION_MISMATCH(PRD0006)", example = "1.2") @NotNull
                    String version) {}
}
