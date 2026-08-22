package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.domain.model.SignupTerm;
import io.swagger.v3.oas.annotations.media.Schema;

// 회원가입 약관 한 건의 API 응답 필드를 표현한다.
public record SignupTermResponse(
        @Schema(description = "회원가입 약관 식별자", example = "TERMS_SERVICE")
        String termsId,

        @Schema(description = "약관 업무 코드", example = "SERVICE_USE")
        String termsCode,

        @Schema(description = "약관 버전", example = "1.0")
        String version,

        @Schema(description = "약관 제목", example = "전자금융거래 기본약관")
        String title,

        @Schema(description = "약관 전문", example = "제1조(목적) 이 약관은...")
        String content,

        @Schema(description = "필수 동의 약관 여부", example = "true")
        boolean isRequired,

        @Schema(description = "동의 전 전문 열람이 필요한 약관 여부", example = "true")
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
