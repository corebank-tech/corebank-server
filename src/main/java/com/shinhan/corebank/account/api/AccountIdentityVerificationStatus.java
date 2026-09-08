package com.shinhan.corebank.account.api;

// 계좌 기반 본인확인의 외부 공개 결과 상태를 정의한다.
public enum AccountIdentityVerificationStatus {
    VERIFIED,
    INFORMATION_MISMATCH,
    PASSWORD_MISMATCH,
    LOCKED
}
