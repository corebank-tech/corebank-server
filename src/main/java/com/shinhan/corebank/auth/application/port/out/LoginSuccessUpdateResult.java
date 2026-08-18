package com.shinhan.corebank.auth.application.port.out;

// 로그인 성공 상태 저장의 완료 또는 동시 잠금 결과
public enum LoginSuccessUpdateResult {
    COMPLETED,
    ACCOUNT_LOCKED
}
