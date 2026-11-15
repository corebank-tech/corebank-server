package com.shinhan.corebank.account.api;

// 다른 모듈에 계좌 소유권과 비밀번호 검증 기능을 공개한다.
public interface AccountIdentityVerifier {

    /**
     * 계좌 소유권과 비밀번호를 검증한다.
     * 계좌비밀번호가 잠긴 경우 APW0101 BusinessException을 발생시킨다.
     */
    AccountIdentityVerificationResult verify(AccountIdentityVerification verification);
}
