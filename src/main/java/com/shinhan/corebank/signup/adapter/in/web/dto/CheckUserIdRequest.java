package com.shinhan.corebank.signup.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

// 중복확인할 회원가입 아이디를 전달한다.
public record CheckUserIdRequest(
        @NotBlank String userId
) {
}
