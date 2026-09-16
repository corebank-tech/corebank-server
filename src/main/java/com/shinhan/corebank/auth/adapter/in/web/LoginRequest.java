package com.shinhan.corebank.auth.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NonNull;

// 로그인 API의 아이디와 비밀번호 요청
public record LoginRequest(
        @Schema(description = "로그인 아이디", example = "corebank01") @NotBlank(message = "아이디는 필수입니다.") String userId,
        @Schema(
                        description = "로그인 비밀번호",
                        example = "Corebank!1234",
                        format = "password",
                        accessMode = Schema.AccessMode.WRITE_ONLY)
                @NotBlank(message = "비밀번호는 필수입니다.")
                String password) {

    // 비밀번호가 로그에 노출되지 않도록 문자열 표현을 제한
    @Override
    public @NonNull String toString() {
        return "LoginRequest[" + "userId=" + userId + ", password=[PROTECTED]" + ']';
    }
}
