package com.shinhan.corebank.transfer.api;

import java.time.LocalDateTime;

/** 성공 이체 한 건. refId는 transfer_id이고 Agent 보고서의 근거 ID로 쓴다. */
public record SucceededTransfer(long refId, long amount, LocalDateTime transferredAt, String depositAccountNumber) {}
