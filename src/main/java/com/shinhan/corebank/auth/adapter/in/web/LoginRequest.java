package com.shinhan.corebank.auth.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.NonNull;

// 로그인 API의 아이디와 비밀번호 요청
public record LoginRequest(
        // DB 정렬 규칙이 악센트·전각 문자를 ASCII와 같게 보므로 ASCII만 받아 변형 아이디로 계정 존재를 가늠하지 못하게 한다(#900)
        @Schema(description = "로그인 아이디", example = "corebank01")
                @NotBlank(message = "아이디는 필수입니다.")
                @Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$", message = "아이디는 영문, 숫자, 밑줄(_), 하이픈(-)으로 20자 이내여야 합니다.")
                String userId,
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
