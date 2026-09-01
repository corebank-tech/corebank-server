package com.shinhan.corebank.account.domain;

// 계좌비밀번호 인증 토큰을 로그인 고객과 검증 계좌에 귀속한다.
public record AccountPasswordAuthTokenPayload(Long customerId, Long accountId) {}
