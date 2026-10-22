package com.shinhan.corebank.subscription.api;

/**
 * 고객 요청에 따른 예적금 만기해지 명령.
 *
 * <p>customerId는 인증된 고객으로부터 얻고, accountId는 요청 경로에서 받는다.
 * 적용금리, 계산 기간, 원리금 수취 계좌는 서버가 가입 정보에서 조회한다.
 */
public record MaturityTerminationCommand(Long customerId, Long accountId, String accountPasswordAuthToken) {}
