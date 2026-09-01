package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.ValidateSignupResult;
import io.swagger.v3.oas.annotations.media.Schema;

// tempSignupToken과 남은 유효시간을 HTTP 응답으로 반환한다.
public record ValidateSignupResponse(
        @Schema(
                        description = "회원가입 확인정보 조회·가입 완료에 사용할 임시 가입 토큰",
                        example = "TEMP_SIGNUP_7xP9qK2RmY5vLw8ZbC6dE4fG1hH0jM3n")
                String tempSignupToken,
        @Schema(description = "임시 가입 토큰 남은 유효시간(초)", example = "1800") long expiresIn) {

    public static ValidateSignupResponse from(ValidateSignupResult result) {
        return new ValidateSignupResponse(result.tempSignupToken(), result.expiresIn());
    }
}
