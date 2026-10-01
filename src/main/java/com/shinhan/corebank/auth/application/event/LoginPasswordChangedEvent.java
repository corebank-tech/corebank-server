package com.shinhan.corebank.auth.application.event;

// 비밀번호 변경 트랜잭션 커밋 후 해당 고객의 모든 로그인 세션을 만료시키기 위한 이벤트다.
public record LoginPasswordChangedEvent(Long customerId) {}
