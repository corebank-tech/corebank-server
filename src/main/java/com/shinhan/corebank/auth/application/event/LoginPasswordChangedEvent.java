package com.shinhan.corebank.auth.application.event;

// 비밀번호 변경 트랜잭션 커밋 전에 해당 고객의 JDBC 로그인 세션을 모두 삭제하기 위한 이벤트다.
public record LoginPasswordChangedEvent(Long customerId) {}
