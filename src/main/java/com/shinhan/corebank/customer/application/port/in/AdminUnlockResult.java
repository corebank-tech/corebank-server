package com.shinhan.corebank.customer.application.port.in;

// 관리자 잠금 해제 후 대상 고객의 로그인 상태.
public record AdminUnlockResult(Long customerId, boolean accountLocked, int loginFailureCount) {}
