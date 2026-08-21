package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.ValidateSignupResult;

// tempSignupToken과 남은 유효시간을 HTTP 응답으로 반환한다.
public record ValidateSignupResponse(
        String tempSignupToken,
        long expiresIn
) {

    public static ValidateSignupResponse from(ValidateSignupResult result) {
        return new ValidateSignupResponse(
                result.tempSignupToken(),
                result.expiresIn()
        );
    }
}
