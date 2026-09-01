package com.shinhan.corebank.account.domain;

// 계좌비밀번호 검증 결과와 최신 실패 상태를 반환한다.
public record AccountPasswordAttemptResult(
        Long accountId, boolean matched, int errorCount, int remainingAttempts, boolean locked) {}
