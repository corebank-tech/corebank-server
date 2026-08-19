package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.CheckTermsAgreementCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CheckTermsAgreementRequest(
        @NotEmpty
        List<@Valid AgreedTermRequest> agreedTerms
) {

    public CheckTermsAgreementCommand toCommand() {
        return new CheckTermsAgreementCommand(
                agreedTerms.stream()
                        .map(AgreedTermRequest::toCommand)
                        .toList()
        );
    }
}
