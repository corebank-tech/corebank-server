package com.shinhan.corebank.signup.application.port.in;

import com.shinhan.corebank.signup.domain.model.SignupConfirmation;

// 회원가입 4단계에 표시할 마스킹된 확인정보를 반환한다.
public record SignupConfirmationResult(
        String userName,
        String userId,
        String birthDate,
        String phoneNumber,
        String email
) {

    public static SignupConfirmationResult from(SignupConfirmation value) {
        return new SignupConfirmationResult(
                value.userName(),
                value.userId(),
                value.birthDate(),
                value.phoneNumber(),
                value.email()
        );
    }
}
