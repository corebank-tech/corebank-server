package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.CheckTermsAgreementCommand;
import jakarta.validation.constraints.NotBlank;

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
