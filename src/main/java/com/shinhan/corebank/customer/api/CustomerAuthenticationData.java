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
    // 계정 구분. 문자열로 넘기면 호출하는 쪽이 오타를 컴파일에서 못 잡는다.
    private final CustomerRole role;
}
