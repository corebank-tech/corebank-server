package com.shinhan.corebank.signup.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// 중복확인할 회원가입 아이디를 전달한다.
public record CheckUserIdRequest(
        @Schema(
                        description = "중복확인할 로그인 아이디. 영문 소문자로 시작하는 영문 소문자·숫자 6~16자리",
                        example = "corebank01",
                        pattern = "^[a-z][a-z0-9]{5,15}$")
                @NotBlank
                String userId) {}
