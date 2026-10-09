package com.shinhan.corebank.common.authtoken;

// 다른 패키지 테스트가 토큰 원문으로 auth_token 행을 찾거나 지울 때 쓴다.
public final class AuthTokenTestRows {

    private AuthTokenTestRows() {}

    public static String hashOf(String token) {
        return AuthTokenStore.hash(token);
    }
}
