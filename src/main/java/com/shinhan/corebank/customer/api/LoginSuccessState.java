package com.shinhan.corebank.customer.api;

// 로그인 성공 상태 갱신의 완료, 동시 잠금 또는 이용정지 결과
public enum LoginSuccessState {
    COMPLETED,
    ACCOUNT_LOCKED,
    ACCOUNT_SUSPENDED
}
