package com.shinhan.corebank.customer.api;

// 저장 완료된 고객의 로그인 실패 횟수와 잠금 상태
public record LoginFailureState(
        int loginFailureCount,
        boolean accountLocked
) {
}