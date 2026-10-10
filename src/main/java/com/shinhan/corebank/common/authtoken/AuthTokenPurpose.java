package com.shinhan.corebank.common.authtoken;

// 토큰 종류. 같은 원문이라도 용도가 다르면 다른 토큰이다 (Redis 키 접두어 역할).
public enum AuthTokenPurpose {
    TERMS_AUTH,
    USER_ID_CHECK,
    EMAIL_VERIFICATION,
    ACCOUNT_AUTH,
    TEMP_SIGNUP,
    OTP_AUTH,
    ACCOUNT_PASSWORD_AUTH
}
