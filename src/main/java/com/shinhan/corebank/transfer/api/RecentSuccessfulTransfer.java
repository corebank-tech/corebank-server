package com.shinhan.corebank.transfer.api;

import java.time.LocalDateTime;

/** 훅 C가 반복 묶음과 과거 행동 연결을 계산할 때 쓰는 커밋된 성공 이체 최소 정보. */
public record RecentSuccessfulTransfer(long refId, long amount, LocalDateTime transferredAt) {}
