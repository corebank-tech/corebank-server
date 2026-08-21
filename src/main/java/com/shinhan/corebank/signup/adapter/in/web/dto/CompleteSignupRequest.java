package com.shinhan.corebank.signup.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

// 회원가입 완료 HTTP 요청의 임시 가입 토큰을 표현한다.
public record CompleteSignupRequest(
        @NotBlank String tempSignupToken
) {
}
