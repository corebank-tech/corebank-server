package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionExecuteUseCase.ProductSubscriptionExecuteCommand;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionExecuteUseCase.ProductSubscriptionExecuteCommand.AgreedTerms;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ProductSubscriptionExecuteRequest(
        @Schema(description = "가입할 상품의 내부 식별자", example = "1") @NotNull Long productId,
        @Schema(description = "가입금액. 사전검증과 동일한 조건(최소·최대·입력단위)을 다시 검사한다", example = "1000000") @NotNull @Positive
                Long subscriptionAmount,
        @Schema(description = "가입기간(월)", example = "12") @NotNull @Positive Integer termMonths,
        @Schema(description = "출금계좌(가입금 이체 및 향후 자동납입 계좌)의 내부 식별자", example = "101") @NotNull Long withdrawalAccountId,
        @Schema(description = "신규 개설 계좌의 비밀번호(4자리 숫자)", example = "1234") @NotNull @Pattern(regexp = "^[0-9]{4}$")
                String newAccountPassword,
        @Schema(description = "신규 계좌 비밀번호 확인값. newAccountPassword와 다르면 APW0002", example = "1234")
                @NotNull
                @Pattern(regexp = "^[0-9]{4}$")
                String newAccountPasswordConfirm,
        @Schema(description = "계좌비밀번호 검증으로 발급된 일회용 인증 토큰", example = "ACC_PWD_9aB3cF8dE2xY7zL1kM0pN4qR5sT6uV") @NotBlank
                String accountPasswordAuthToken,
        @Schema(
                        description = "OTP 검증으로 발급된 인증 토큰. transactionData가 이 요청의 가입내용과 다르면 OTP0102",
                        example = "OTP_AUTH_7xP9qK2RmY5vLw8ZbC6dE4fG1hH0jM3n")
                @NotBlank
                String otpAuthToken,
        // 상품에 필수 약관이 하나도 없으면 빈 배열이 정상 요청이다(ProductSubscriptionValidationRequest와
        // 동일한 이유로 @NotEmpty가 아니라 @NotNull).
        @Schema(description = "약관 동의 목록. 상품에 필수 약관이 없으면 빈 배열([])") @NotNull List<@Valid AgreedTermsItem> agreedTerms
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
                customerId,
                productId,
                subscriptionAmount,
                termMonths,
                withdrawalAccountId,
                newAccountPassword,
                newAccountPasswordConfirm,
                accountPasswordAuthToken,
                otpAuthToken,
                agreed);
    }

    public record AgreedTermsItem(
            @Schema(description = "동의한 약관의 내부 식별자", example = "3") @NotNull Long termsId,
            @Schema(description = "동의한 약관의 버전", example = "1.2") @NotNull String version) {}
}
