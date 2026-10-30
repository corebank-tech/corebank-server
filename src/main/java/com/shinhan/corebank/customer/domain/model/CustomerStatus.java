package com.shinhan.corebank.customer.domain.model;

// 관리자가 정하는 고객 계정 상태. 비밀번호 오류 잠금(accountLocked)과 별개다.
public enum CustomerStatus {
    ACTIVE,
    SUSPENDED
}
