package com.shinhan.corebank.transfer.api;

import java.time.LocalDateTime;

/** P6이 보류 원본과 현재 상태를 확인할 때 사용하는 읽기 전용 공개 계약. */
public record TransferHoldForFds(
        long transferHoldId, long customerId, String flowId, String status, LocalDateTime heldAt) {}
