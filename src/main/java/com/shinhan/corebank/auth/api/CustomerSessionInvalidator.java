package com.shinhan.corebank.auth.api;

// 고객 상태·비밀번호 변경 트랜잭션에서 모든 WAS의 세션을 종료하는 공개 계약이다.
public interface CustomerSessionInvalidator {
    void invalidateAll(Long customerId);
}
