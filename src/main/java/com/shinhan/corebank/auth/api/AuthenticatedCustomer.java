package com.shinhan.corebank.auth.api;

import java.util.Objects;

// 인증된 고객의 세션 정보를 생성
public final class AuthenticatedCustomer {

    private final Long customerId;
    private final String userId;
    private final String userName;

    public AuthenticatedCustomer(
            Long customerId,
            String userId,
            String userName
    ) {
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        this.userId = userId;
        this.userName = userName;
    }

    public Long customerId() {
        return customerId;
    }

    public String userId() {
        return userId;
    }

    public String userName() {
        return userName;
    }


    // 세 필드의 값이 모두 같은 인증 고객 객체인지 비교
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuthenticatedCustomer other)) {
            return false;
        }
        return Objects.equals(customerId, other.customerId)
                && Objects.equals(userId, other.userId)
                && Objects.equals(userName, other.userName);
    }


    // equals()와 동일한 필드를 사용해 객체의 해시값을 생성
    @Override
    public int hashCode() {
        return Objects.hash(customerId, userId, userName);
    }


    //로그 출력 시 개인정보를 제외하고 내부 고객 PK만 표시
    @Override
    public String toString() {
        return "AuthenticatedCustomer{" +
                "customerId=" + customerId +
                '}';
    }
}
