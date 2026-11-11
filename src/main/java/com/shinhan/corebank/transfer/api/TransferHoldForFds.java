package com.shinhan.corebank.transfer.api;

import java.time.LocalDateTime;

/**
 * P6이 보류 원본과 현재 상태를 확인할 때 사용하는 읽기 전용 공개 계약. 끝난 보류도 반환한다(#971).
 *
 * recipientBankCode 는 저장하지 않고 당행 "088"로 채운다. 보류는 당행 즉시이체에만 생긴다.
 *
 * actorId 와 reasonCode 는 상태마다 뜻이 다르니 status 를 먼저 보고 읽는다.
 *   REJECTED           actorId=거절한 관리자, reasonCode=거절 사유
 *   EXECUTING, EXECUTED, FAILED  actorId=승인한 관리자(스케줄러 실행이면 null), reasonCode=FAILED일 때 실패 오류코드
 *   CANCELLED          actorId=취소한 고객
 *   HELD, EXPIRED      actorId·reasonCode 없음
 * completedAt 은 끝난 상태가 된 시각이고, 끝나지 않았으면 null 이다.
 */
public record TransferHoldForFds(
        long transferHoldId,
        long customerId,
        String flowId,
        String status,
        LocalDateTime heldAt,
        long amount,
        String depositAccountNumber,
        String policyCode,
        long withdrawalAccountId,
        String recipientBankCode,
        String releaseMode,
        Long actorId,
        String reasonCode,
        LocalDateTime completedAt) {}
