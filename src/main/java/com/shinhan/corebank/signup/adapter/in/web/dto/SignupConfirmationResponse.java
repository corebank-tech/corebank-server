package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.SignupConfirmationResult;

// 회원가입 확인 화면의 마스킹된 정보를 HTTP 응답으로 반환한다.
public record SignupConfirmationResponse(
        String userName,
        String userId,
        String birthDate,
        String phoneNumber,
        String email
) {

    public static SignupConfirmationResponse from(
            SignupConfirmationResult result
    ) {
        return new SignupConfirmationResponse(
                result.userName(),
                result.userId(),
                result.birthDate(),
                result.phoneNumber(),
                result.email()
        );
    }
}
