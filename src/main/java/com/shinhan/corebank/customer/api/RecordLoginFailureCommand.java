package com.shinhan.corebank.customer.api;

// 고객의 로그인 실패를 1회 기록하기 위한 명령
public record RecordLoginFailureCommand(Long customerId) {}
