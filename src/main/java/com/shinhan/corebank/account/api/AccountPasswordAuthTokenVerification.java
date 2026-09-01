package com.shinhan.corebank.account.api;

// 업무 모듈이 검증할 계좌비밀번호 인증 토큰과 고객·계좌를 전달한다.
public record AccountPasswordAuthTokenVerification(String accountPasswordAuthToken, Long customerId, Long accountId) {}
