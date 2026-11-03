package com.shinhan.corebank.subscription.api;

/** 고객 ID는 세션에서, 계좌 ID는 경로에서 가져온다. 수취계좌·금리는 서버가 결정한다. */
public record EarlyTerminationCommand(Long customerId, Long accountId, String accountPasswordAuthToken) {}
