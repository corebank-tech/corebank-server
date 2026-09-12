package com.shinhan.corebank.account.api;

// 다른 모듈에 계좌 소유권과 비밀번호 검증 기능을 공개한다.
public interface AccountIdentityVerifier {

    AccountIdentityVerificationResult verify(AccountIdentityVerification verification);
}
