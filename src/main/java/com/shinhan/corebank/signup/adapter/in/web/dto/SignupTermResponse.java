package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.domain.model.SignupTerm;

// 회원가입 약관 한 건의 API 응답 필드를 표현한다.
public record SignupTermResponse(
        String termsId,
        String termsCode,
        String version,
        String title,
        String content,
        boolean isRequired,
        boolean viewRequired
) {

    public static SignupTermResponse from(SignupTerm term) {
        return new SignupTermResponse(
                term.termsId(),
                term.termsCode(),
                term.version(),
                term.title(),
                term.content(),
                term.required(),
                term.viewRequired()
        );
    }
}
