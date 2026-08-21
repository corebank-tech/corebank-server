package com.shinhan.corebank.signup.application.port.out;

// 회원가입 단계별 인증 완료 토큰 생성 기능을 정의한다.
public interface AuthTokenGeneratorPort {

    String generateTermsAuthToken();

    String generateUserIdCheckToken();

    String generateEmailVerificationToken();

    String generateAccountAuthToken();

    String generateTempSignupToken();
}
