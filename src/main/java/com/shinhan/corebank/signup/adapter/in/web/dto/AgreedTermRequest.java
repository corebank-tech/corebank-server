package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.CheckTermsAgreementCommand;
import jakarta.validation.constraints.NotBlank;

// 약관 한 건의 동의·열람 여부와 버전을 전달한다.
public record AgreedTermRequest(
        @NotBlank
        String termsId,

        @NotBlank
        String version,

        boolean isAgreed,

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
