package com.shinhan.corebank.customer.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

// customer 모듈이 외부에 제공하는 로그인 고객 인증정보
@Getter
@AllArgsConstructor
public final class CustomerAuthenticationData {

    private final Long customerId;
    private final String userId;
    private final String passwordHash;
    private final String userName;
    private final int loginFailureCount;
    private final boolean accountLocked;
}
