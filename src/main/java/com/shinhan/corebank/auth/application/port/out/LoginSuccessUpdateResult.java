package com.shinhan.corebank.auth.application.port.out;

// 로그인 성공 상태 저장의 완료, 동시 잠금 또는 이용정지 결과
public enum LoginSuccessUpdateResult {
    COMPLETED,
    ACCOUNT_LOCKED,
    ACCOUNT_SUSPENDED
}
