package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.CheckUserIdResult;
import io.swagger.v3.oas.annotations.media.Schema;

// 아이디 사용 가능 여부와 중복확인 완료 토큰을 반환한다.
public record CheckUserIdResponse(
        @Schema(description = "아이디 사용 가능 여부", example = "true") boolean isAvailable,
        @Schema(description = "회원가입 입력 검증에서 한 번 사용할 수 있는 아이디 중복확인 토큰", example = "USER_ID_CHECK_7xP9qK2RmY5vLw8Z")
                String userIdCheckToken,
        @Schema(description = "중복확인 토큰 남은 유효시간(초)", example = "1800") long expiresIn) {

    public static CheckUserIdResponse from(CheckUserIdResult result) {
        return new CheckUserIdResponse(result.isAvailable(), result.userIdCheckToken(), result.expiresIn());
    }
}
