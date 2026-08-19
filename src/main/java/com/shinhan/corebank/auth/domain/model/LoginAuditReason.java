package com.shinhan.corebank.auth.domain.model;

// 로그인 감사 로그에 허용되는 처리 결과 사유
public enum LoginAuditReason {
    SUCCESS,
    INVALID_CREDENTIALS,
    ACCOUNT_LOCKED
}
