package com.shinhan.corebank.auth.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
// 로그인 인증에 필요한 고객 정보를 담는 불변 조회 모델
public final class LoginCustomer {

    private final Long customerId;
    private final String userId;
    private final String passwordHash;
    private final String userName;
    private final int loginFailureCount;
    private final boolean accountLocked;
}
