package com.shinhan.corebank.auth.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NonNull;

// 로그인 API의 아이디와 비밀번호 요청
public record LoginRequest(
        @NotBlank(message = "아이디는 필수입니다.")
        String userId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {

    // 비밀번호가 로그에 노출되지 않도록 문자열 표현을 제한
    @Override
    public @NonNull String toString() {
        return "LoginRequest[" +
                "userId=" + userId +
                ", password=[PROTECTED]" +
                ']';
    }
}
