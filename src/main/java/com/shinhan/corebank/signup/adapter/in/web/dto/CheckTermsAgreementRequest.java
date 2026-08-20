package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.CheckTermsAgreementCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// 검증할 회원가입 약관 동의 목록을 전달한다.
public record CheckTermsAgreementRequest(
        @NotEmpty
        List<@NotNull @Valid AgreedTermRequest> agreedTerms
) {

    public CheckTermsAgreementCommand toCommand() {
        return new CheckTermsAgreementCommand(
                agreedTerms.stream()
                        .map(AgreedTermRequest::toCommand)
                        .toList()
        );
    }
}
