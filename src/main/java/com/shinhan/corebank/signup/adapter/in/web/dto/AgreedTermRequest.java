package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.CheckTermsAgreementCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// 약관 한 건의 동의·열람 여부와 버전을 전달한다.
public record AgreedTermRequest(
        @Schema(description = "회원가입 약관 식별자", example = "TERMS_SERVICE")
        @NotBlank
        String termsId,

        @Schema(description = "동의한 약관 버전", example = "1.0")
        @NotBlank
        String version,

        @Schema(description = "약관 동의 여부", example = "true")
        boolean isAgreed,

        @Schema(description = "약관 전문 열람 여부", example = "true")
        boolean isRead
) {

    public CheckTermsAgreementCommand.Agreement toCommand() {
        return new CheckTermsAgreementCommand.Agreement(
                termsId,
                version,
                isAgreed,
                isRead
        );
    }
}
