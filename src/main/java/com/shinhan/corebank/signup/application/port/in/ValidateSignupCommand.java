package com.shinhan.corebank.signup.application.port.in;

// 회원가입 입력값과 단계별 인증 토큰을 전달한다.
public record ValidateSignupCommand(
        String termsAuthToken,
        String accountAuthToken,
        String userIdCheckToken,
        String emailVerificationToken,
        String tempSignupToken,
        String userId,
        String userPassword,
        String userPasswordConfirm,
        String email,
        String phoneNumber
) {

    public boolean isEditRequest() {
        return tempSignupToken != null && !tempSignupToken.isBlank();
    }
}
