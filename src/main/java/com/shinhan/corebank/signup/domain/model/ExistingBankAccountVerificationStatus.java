package com.shinhan.corebank.signup.domain.model;

// Mock 은행 원장의 실명·계좌 검증 결과 유형을 정의한다.
public enum ExistingBankAccountVerificationStatus {
    VERIFIED,
    INFORMATION_MISMATCH,
    PASSWORD_MISMATCH,
    LOCKED
}
