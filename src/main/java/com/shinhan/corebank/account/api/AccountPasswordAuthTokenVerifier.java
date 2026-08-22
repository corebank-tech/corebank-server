package com.shinhan.corebank.account.api;

// 다른 업무 모듈에 토큰 검증과 일회성 소비 기능을 공개한다.
public interface AccountPasswordAuthTokenVerifier {

    void verifyAndConsume(
            AccountPasswordAuthTokenVerification verification
    );
}
