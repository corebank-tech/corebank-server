package com.shinhan.corebank.signup.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// 회원가입 완료 HTTP 요청의 임시 가입 토큰을 표현한다.
public record CompleteSignupRequest(
        @Schema(description = "회원가입 입력 검증 후 발급된 임시 가입 토큰", example = "TEMP_SIGNUP_7xP9qK2RmY5vLw8ZbC6dE4fG1hH0jM3n")
                @NotBlank
                String tempSignupToken) {}
