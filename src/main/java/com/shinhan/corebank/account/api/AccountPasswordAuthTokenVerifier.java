package com.shinhan.corebank.account.api;

// 다른 업무 모듈에 토큰 검증과 일회성 소비 기능을 공개한다.
public interface AccountPasswordAuthTokenVerifier {

    void verifyAndConsume(AccountPasswordAuthTokenVerification verification);

    // 고객 단위 업무에서 토큰의 고객 소유권만 검증하고 일회성으로 소비한다.
    void verifyAndConsume(String accountPasswordAuthToken, Long customerId);
}
