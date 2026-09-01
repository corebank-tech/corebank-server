package com.shinhan.corebank.transfer.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * LMT 코드는 원래 P1(한도 도메인) 소유지만, 한도 검증이 이체 실행 흐름(P4) 안에서 일어나므로
 * 도메인 구현 전까지 P4가 던진다(docs/api_conventions.md §8-2). AutoTransferErrorCode.AUT0006과
 * 달리 임시 대체 코드를 새로 만들지 않고, 이미 문서에 확정된 실제 LMT 코드값을 그대로 쓴다 —
 * P1의 실제 LmtErrorCode가 구현돼도 값이 같아 교체가 필요 없다.
 */
@Getter
@RequiredArgsConstructor
public enum LimitErrorCode implements ErrorCode {
    INSUFFICIENT_WITHDRAWABLE_AMOUNT("LMT0001", 400, "출금가능금액이 부족합니다."),
    ONE_TIME_LIMIT_EXCEEDED("LMT0002", 400, "1회 이체한도를 초과했습니다."),
    DAILY_LIMIT_EXCEEDED("LMT0003", 400, "1일 이체한도를 초과했습니다.");

    private final String code;
    private final int status;
    private final String message;
}
