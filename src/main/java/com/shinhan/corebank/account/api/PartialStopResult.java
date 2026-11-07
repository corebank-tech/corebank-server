package com.shinhan.corebank.account.api;

/**
 * 일부 지급정지 전환 결과.
 *
 * @param requestedAmount 호출자가 요청한 보류액
 * @param appliedAmount 실제로 묶은 보류액. 잔액에서 다른 사건 보류액을 뺀 금액이 상한이다
 */
public record PartialStopResult(long requestedAmount, long appliedAmount) {}
