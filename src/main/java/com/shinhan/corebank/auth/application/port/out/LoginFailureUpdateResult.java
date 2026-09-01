package com.shinhan.corebank.auth.application.port.out;

// 고객 모듈에 저장된 최신 로그인 실패 상태
public record LoginFailureUpdateResult(int errorCount, boolean accountLocked) {}
